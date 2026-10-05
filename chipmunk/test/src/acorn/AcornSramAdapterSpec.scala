package chipmunk.test.acorn

import scala.collection.mutable
import scala.util.Random

import chisel3.*
import chisel3.util.{ShiftRegister, SRAM}

import chipmunk.*
import chipmunk.acorn.*
import chipmunk.test.ChipmunkFlatSpec

private object AcornSramAdapterSpecDut {
  final class Harness(config: AcornSramConfig) extends Module with RequireAsyncReset {
    val io = IO(new Bundle {
      val access      = Slave(new AcornIO(config.params))
      val readEnable  = Output(Bool())
      val writeEnable = Output(Bool())
      val readWord    = Output(UInt(config.params.addrWidth.W))
      val writeWord   = Output(UInt(config.params.addrWidth.W))
    })

    val adapter = Module(new AcornSramAdapter(config))
    adapter.io.access <> io.access

    private val single = config.portMode == AcornSramPortMode.SinglePort
    private val memory = SRAM.masked(
      config.numWords,
      Vec(config.params.bytesPerWord, UInt(8.W)),
      numReadPorts = if (single) 0 else 1,
      numWritePorts = if (single) 0 else 1,
      numReadwritePorts = if (single) 1 else 0,
    )

    if (config.readLatency == 1) {
      adapter.io.sram <> memory
    }

    if (single) {
      val source = adapter.io.sram.readwritePorts(0)
      val target = memory.readwritePorts(0)
      if (config.readLatency > 1) {
        target.address   := source.address
        target.enable    := source.enable
        target.isWrite   := source.isWrite
        target.writeData := source.writeData
        target.mask.get  := source.mask.get
        source.readData  := ShiftRegister(target.readData, config.readLatency - 1)
      }
      io.readEnable  := source.enable && !source.isWrite
      io.writeEnable := source.enable && source.isWrite
      io.readWord    := source.address
      io.writeWord   := source.address
    } else {
      val readSource  = adapter.io.sram.readPorts(0)
      val writeSource = adapter.io.sram.writePorts(0)
      val readTarget  = memory.readPorts(0)
      val writeTarget = memory.writePorts(0)
      if (config.readLatency > 1) {
        readTarget.address   := readSource.address
        readTarget.enable    := readSource.enable
        readSource.data      := ShiftRegister(readTarget.data, config.readLatency - 1)
        writeTarget.address  := writeSource.address
        writeTarget.enable   := writeSource.enable
        writeTarget.data     := writeSource.data
        writeTarget.mask.get := writeSource.mask.get
      }
      io.readEnable  := readSource.enable
      io.writeEnable := writeSource.enable
      io.readWord    := readSource.address
      io.writeWord   := writeSource.address
    }
  }
}

class AcornSramAdapterSpec extends ChipmunkFlatSpec {
  private case class Write(address: Int, data: BigInt, strobe: Int)
  private case class Result(data: BigInt, error: Boolean)

  for {
    mode                         <- AcornSramPortMode.values.toSeq
    (width, addressWidth, words) <- Seq((8, 3, 8), (32, 2, 1), (64, 4, 2))
  } {
    "AcornSramAdapter" should s"handle $width-bit words and a full $addressWidth-bit address range with $mode" in {
      val config = AcornSramConfig(AcornParams(width, addressWidth), words, mode)
      simulate(new AcornSramAdapterSpecDut.Harness(config)) { dut =>
        init(dut)
        val port    = dut.io.access
        val address = config.sizeBytes - config.params.bytesPerWord
        val data    = (BigInt(1) << width) - 0x53
        port.wr.cmd.valid #= true.B
        port.wr.cmd.bits.addr #= address.U
        port.wr.cmd.bits.data #= data.U
        port.wr.cmd.bits.strobe #= ((BigInt(1) << config.params.bytesPerWord) - 1).U
        port.wr.cmd.ready expect true.B
        dut.clock.step()
        port.wr.cmd.valid #= false.B
        port.wr.rsp.valid expect true.B
        port.wr.rsp.bits.error expect false.B
        port.rd.cmd.valid #= true.B
        port.rd.cmd.bits.addr #= address.U
        port.rd.cmd.ready expect true.B
        dut.clock.step()
        port.rd.cmd.valid #= false.B
        dut.clock.step()
        port.rd.rsp.valid expect true.B
        port.rd.rsp.bits.data expect data.U
        port.rd.rsp.bits.error expect false.B
        dut.clock.step()
      }
    }
  }

  private def init(dut: AcornSramAdapterSpecDut.Harness): Unit = {
    val port = dut.io.access
    port.rd.cmd.valid #= false.B
    port.rd.cmd.bits.addr #= 0.U
    port.rd.rsp.ready #= true.B
    port.wr.cmd.valid #= false.B
    port.wr.cmd.bits.addr #= 0.U
    port.wr.cmd.bits.data #= 0.U
    port.wr.cmd.bits.strobe #= 0.U
    port.wr.rsp.ready #= true.B
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
  }

  private def write(dut: AcornSramAdapterSpecDut.Harness, address: Int, data: BigInt): Unit = {
    val port = dut.io.access.wr
    port.cmd.valid #= true.B
    port.cmd.bits.addr #= address.U
    port.cmd.bits.data #= data.U
    port.cmd.bits.strobe #= 15.U
    port.cmd.ready expect true.B
    dut.clock.step()
    port.cmd.valid #= false.B
    port.rsp.valid expect true.B
    port.rsp.bits.error expect false.B
    dut.clock.step()
  }

  for {
    mode     <- AcornSramPortMode.values.toSeq
    latency  <- Seq(1, 3)
    capacity <- Seq(1, 4)
  } {
    "AcornSramAdapter" should s"preserve data and ordering with $mode, latency $latency, capacity $capacity" in {
      val config = AcornSramConfig(AcornParams(32, 6), 5, mode, latency, AcornOutstanding(capacity, capacity))
      simulate(new AcornSramAdapterSpecDut.Harness(config)) { dut =>
        init(dut)
        val memory = Array.tabulate(5)(index => BigInt("12345600", 16) + index)
        memory.indices.foreach(index => write(dut, index * 4, memory(index)))
        val random = new Random(421 + latency * 17 + capacity)
        val reads  = Vector.tabulate(100) { index =>
          index % 8 match {
            case 0 => 1
            case 1 => 20
            case 2 => 60
            case _ => random.nextInt(5) * 4
          }
        }
        val writes = Vector.tabulate(100) { index =>
          val address = index % 9 match {
            case 0 => 3
            case 1 => 20
            case 2 => 60
            case _ => random.nextInt(5) * 4
          }
          Write(address, BigInt(32, random), index % 16)
        }
        val expectedReads  = mutable.Queue.empty[Result]
        val expectedWrites = mutable.Queue.empty[Boolean]
        var heldRead       = Option.empty[Result]
        var heldWrite      = Option.empty[Boolean]
        var readSent       = 0
        var writeSent      = 0
        var readReceived   = 0
        var writeReceived  = 0
        var cycle          = 0

        def valid(address: Int): Boolean = address % 4 == 0 && address < 20

        while ((readReceived < reads.size || writeReceived < writes.size) && cycle < 5000) {
          val port = dut.io.access
          port.rd.cmd.valid #= (readSent < reads.size).B
          if (readSent < reads.size) port.rd.cmd.bits.addr #= reads(readSent).U
          port.wr.cmd.valid #= (writeSent < writes.size).B
          if (writeSent < writes.size) {
            val command = writes(writeSent)
            port.wr.cmd.bits.addr #= command.address.U
            port.wr.cmd.bits.data #= command.data.U
            port.wr.cmd.bits.strobe #= command.strobe.U
          }
          val readReady  = cycle >= 40 && (cycle > 4000 || random.nextBoolean())
          val writeReady = cycle >= 65 && (cycle > 4000 || random.nextBoolean())
          port.rd.rsp.ready #= readReady.B
          port.wr.rsp.ready #= writeReady.B

          val readFire    = readSent < reads.size && port.rd.cmd.ready.peek().litToBoolean
          val writeFire   = writeSent < writes.size && port.wr.cmd.ready.peek().litToBoolean
          val readEnable  = dut.io.readEnable.peek().litToBoolean
          val writeEnable = dut.io.writeEnable.peek().litToBoolean
          readEnable shouldBe (readFire && valid(reads(readSent)))
          writeEnable shouldBe (writeFire && valid(writes(writeSent).address) && writes(writeSent).strobe != 0)
          if (readEnable && writeEnable) {
            mode shouldBe AcornSramPortMode.OneReadOneWrite
            dut.io.readWord.peek().litValue should not be dut.io.writeWord.peek().litValue
          }

          if (port.rd.rsp.valid.peek().litToBoolean) {
            expectedReads.nonEmpty shouldBe true
            val current = Result(port.rd.rsp.bits.data.peek().litValue, port.rd.rsp.bits.error.peek().litToBoolean)
            current shouldBe expectedReads.head
            heldRead.foreach(_ shouldBe current)
            if (readReady) {
              expectedReads.dequeue()
              readReceived += 1
              heldRead = None
            } else heldRead = Some(current)
          } else heldRead shouldBe None

          if (port.wr.rsp.valid.peek().litToBoolean) {
            expectedWrites.nonEmpty shouldBe true
            val current = port.wr.rsp.bits.error.peek().litToBoolean
            current shouldBe expectedWrites.head
            heldWrite.foreach(_ shouldBe current)
            if (writeReady) {
              expectedWrites.dequeue()
              writeReceived += 1
              heldWrite = None
            } else heldWrite = Some(current)
          } else heldWrite shouldBe None

          if (readFire) {
            val address = reads(readSent)
            expectedReads.enqueue(if (valid(address)) Result(memory(address / 4), false) else Result(0, true))
            readSent += 1
          }
          if (writeFire) {
            val command = writes(writeSent)
            expectedWrites.enqueue(!valid(command.address))
            if (valid(command.address)) {
              val index = command.address / 4
              for (byte <- 0 until 4 if (command.strobe & (1 << byte)) != 0) {
                val mask = BigInt(255) << (byte * 8)
                memory(index) = (memory(index) & ~mask) | (command.data & mask)
              }
            }
            writeSent += 1
          }
          dut.clock.step()
          cycle += 1
        }
        readReceived shouldBe reads.size
        writeReceived shouldBe writes.size
        expectedReads shouldBe empty
        expectedWrites shouldBe empty
        dut.io.access.rd.cmd.valid #= false.B
        dut.io.access.wr.cmd.valid #= false.B
        dut.clock.step(latency + 4)
        dut.io.access.rd.rsp.valid expect false.B
        dut.io.access.wr.rsp.valid expect false.B
      }
    }
  }

  for (mode <- AcornSramPortMode.values.toSeq) {
    "AcornSramAdapter" should s"sustain one read per cycle with $mode" in {
      val config = AcornSramConfig(AcornParams(32, 4), 4, mode)
      simulate(new AcornSramAdapterSpecDut.Harness(config)) { dut =>
        init(dut)
        val data = Vector.tabulate(4)(index => BigInt(0x12345000 + index))
        data.indices.foreach(index => write(dut, index * 4, data(index)))
        val port = dut.io.access.rd
        for (index <- 0 until 16) {
          port.cmd.valid #= true.B
          port.cmd.bits.addr #= ((index % 4) * 4).U
          port.cmd.ready expect true.B
          port.rsp.valid expect (index >= 2).B
          if (index >= 2) {
            port.rsp.bits.data expect data((index - 2) % 4).U
            port.rsp.bits.error expect false.B
          }
          dut.clock.step()
        }
        port.cmd.valid #= false.B
        for (index <- 14 until 16) {
          port.rsp.valid expect true.B
          port.rsp.bits.data expect data(index % 4).U
          port.rsp.bits.error expect false.B
          dut.clock.step()
        }
        port.rsp.valid expect false.B
      }
    }

    "AcornSramAdapter" should s"resolve same-word conflicts fairly and keep read snapshots with $mode" in {
      val config = AcornSramConfig(AcornParams(32, 4), 4, mode, 1, AcornOutstanding(4, 4))
      simulate(new AcornSramAdapterSpecDut.Harness(config)) { dut =>
        init(dut)
        write(dut, 0, 0x11223344)
        val port = dut.io.access
        port.rd.rsp.ready #= false.B
        port.wr.rsp.ready #= false.B
        port.rd.cmd.valid #= true.B
        port.rd.cmd.bits.addr #= 0.U
        port.wr.cmd.valid #= true.B
        port.wr.cmd.bits.addr #= 0.U
        port.wr.cmd.bits.data #= BigInt("aabbccdd", 16).U
        port.wr.cmd.bits.strobe #= 15.U
        for (cycle <- 0 until 4) {
          port.rd.cmd.ready expect (cycle % 2 == 0).B
          port.wr.cmd.ready expect (cycle % 2 == 1).B
          dut.clock.step()
        }
        port.rd.cmd.valid #= false.B
        port.wr.cmd.valid #= false.B
        dut.clock.step(3)
        port.rd.rsp.valid expect true.B
        port.rd.rsp.bits.data expect 0x11223344.U
        port.rd.rsp.ready #= true.B
        dut.clock.step()
        port.rd.rsp.valid expect true.B
        port.rd.rsp.bits.data expect BigInt("aabbccdd", 16).U
        dut.clock.step()
        port.rd.rsp.valid expect false.B
        port.wr.rsp.ready #= true.B
        dut.clock.step(2)
        port.wr.rsp.valid expect false.B
      }
    }

    "AcornSramAdapter" should s"cancel pending responses on reset while retaining SRAM contents with $mode" in {
      val config = AcornSramConfig(AcornParams(32, 4), 4, mode, 3, AcornOutstanding(4, 4))
      simulate(new AcornSramAdapterSpecDut.Harness(config)) { dut =>
        init(dut)
        write(dut, 0, 0x12345678)
        val port = dut.io.access
        port.rd.rsp.ready #= false.B
        port.wr.rsp.ready #= false.B
        port.rd.cmd.valid #= true.B
        port.rd.cmd.bits.addr #= 0.U
        dut.clock.step()
        port.rd.cmd.valid #= false.B
        port.wr.cmd.valid #= true.B
        port.wr.cmd.bits.addr #= 4.U
        port.wr.cmd.bits.data #= 0x44332211.U
        port.wr.cmd.bits.strobe #= 15.U
        dut.clock.step()
        port.wr.cmd.valid #= false.B
        dut.reset #= true.B
        dut.io.readEnable expect false.B
        dut.io.writeEnable expect false.B
        port.rd.rsp.valid expect false.B
        port.wr.rsp.valid expect false.B
        dut.clock.step()
        dut.reset #= false.B
        port.rd.rsp.ready #= true.B
        port.wr.rsp.ready #= true.B
        dut.clock.step(5)
        port.rd.rsp.valid expect false.B
        port.wr.rsp.valid expect false.B
        for ((address, data) <- Seq(0 -> BigInt(0x12345678), 4 -> BigInt(0x44332211))) {
          port.rd.cmd.valid #= true.B
          port.rd.cmd.bits.addr #= address.U
          port.rd.cmd.ready expect true.B
          dut.clock.step()
          port.rd.cmd.valid #= false.B
          dut.clock.step(3)
          port.rd.rsp.valid expect true.B
          port.rd.rsp.bits.data expect data.U
          port.rd.rsp.bits.error expect false.B
          dut.clock.step()
        }
      }
    }
  }

  "AcornSramAdapter" should "access different words concurrently and drain responses independently with 1R1W" in {
    val config = AcornSramConfig(AcornParams(32, 4), 4, AcornSramPortMode.OneReadOneWrite)
    simulate(new AcornSramAdapterSpecDut.Harness(config)) { dut =>
      init(dut)
      write(dut, 0, 0x11223344)
      write(dut, 4, 0x55667788)
      val port = dut.io.access
      port.rd.rsp.ready #= false.B
      port.wr.rsp.ready #= false.B
      port.rd.cmd.valid #= true.B
      port.rd.cmd.bits.addr #= 0.U
      port.wr.cmd.valid #= true.B
      port.wr.cmd.bits.addr #= 4.U
      port.wr.cmd.bits.data #= BigInt("aabbccdd", 16).U
      port.wr.cmd.bits.strobe #= 5.U
      port.rd.cmd.ready expect true.B
      port.wr.cmd.ready expect true.B
      dut.io.readEnable expect true.B
      dut.io.writeEnable expect true.B
      dut.clock.step()
      port.rd.cmd.valid #= false.B
      port.wr.cmd.valid #= false.B
      port.wr.rsp.valid expect true.B
      port.wr.rsp.bits.error expect false.B
      dut.clock.step()
      port.rd.rsp.valid expect true.B
      port.rd.rsp.bits.data expect 0x11223344.U
      port.wr.rsp.ready #= true.B
      dut.clock.step()
      port.wr.rsp.valid expect false.B
      port.rd.rsp.valid expect true.B
      port.rd.rsp.bits.data expect 0x11223344.U
      port.rd.rsp.ready #= true.B
      dut.clock.step()
      port.rd.cmd.valid #= true.B
      port.rd.cmd.bits.addr #= 4.U
      port.rd.cmd.ready expect true.B
      dut.clock.step()
      port.rd.cmd.valid #= false.B
      dut.clock.step()
      port.rd.rsp.valid expect true.B
      port.rd.rsp.bits.data expect BigInt("55bb77dd", 16).U
      port.rd.rsp.bits.error expect false.B
      dut.clock.step()
    }
  }
}
