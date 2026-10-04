package chipmunk.test.acorn

import scala.collection.mutable
import scala.util.Random

import chisel3.*

import chipmunk.acorn.*

class AcornCrossbarSpec extends AcornSpecSupport {
  "AcornCrossbar" should "support a full global window, one master, and a wider local address with carry" in {
    val config = AcornCrossbarConfig(
      AcornParams(8, 4),
      1,
      Seq(AcornSlaveConfig("memory", 0, 16, localBase = 15)),
      masterOutstanding = AcornOutstanding(1, 1),
      slaveOutstanding = AcornOutstanding(1, 1),
    )
    config.slaves.head.portAddrWidth shouldBe 5
    simulate(new AcornCrossbar(config)) { dut =>
      initMaster(dut.io.ins(0))
      initSlave(dut.io.outs(0))
      reset(dut)
      dut.io.ins(0).rd.cmd.valid #= true.B
      dut.io.ins(0).rd.cmd.bits.addr #= 15.U
      dut.io.outs(0).rd.cmd.ready #= true.B
      dut.io.outs(0).rd.cmd.valid expect true.B
      dut.io.outs(0).rd.cmd.bits.addr expect 30.U
      dut.clock.step()
      dut.io.ins(0).rd.cmd.valid #= false.B
      dut.io.outs(0).rd.rsp.valid #= true.B
      dut.io.outs(0).rd.rsp.bits.data #= 0xa5.U
      dut.io.ins(0).rd.rsp.ready #= true.B
      dut.io.ins(0).rd.rsp.valid expect true.B
      dut.io.ins(0).rd.rsp.bits.data expect 0xa5.U
      dut.clock.step()
    }
  }

  private case class Write(addr: BigInt, data: BigInt, strobe: BigInt)
  private case class ReadResult(data: BigInt, error: Boolean)
  private case class PendingRead(due: Int, result: ReadResult)
  private case class PendingWrite(due: Int, error: Boolean)

  for (depth <- Seq(1, 4)) {
    "AcornCrossbar" should s"route concurrent reads and writes under random stalls with capacity $depth" in {
      val regions = Seq(
        AcornSlaveConfig("bank0", 0x1000, 0x100),
        AcornSlaveConfig("bank1", 0x2000, 0x100, localBase = 0x80),
        AcornSlaveConfig("status", 0x3000, 0x100, writable = false),
      )
      val config = AcornCrossbarConfig(
        AcornParams(32, 16),
        3,
        regions,
        masterOutstanding = AcornOutstanding(depth, depth),
        slaveOutstanding = AcornOutstanding(depth, depth),
        connections = Some(Seq(Set("bank0", "bank1", "status"), Set("bank1"), Set("bank0", "bank1", "status"))),
      )
      val reads = Array.tabulate(3) { master =>
        Vector.tabulate(24) { index =>
          val offset = BigInt(4 * ((master * 7 + index) % 64))
          index % 8 match {
            case 0 | 4 => BigInt(0x1000) + offset
            case 1 | 5 => BigInt(0x2000) + offset
            case 2 | 6 => BigInt(0x3000) + offset
            case 3     => BigInt(0x4000) + offset
            case _     => BigInt(0x1001) + offset
          }
        }
      }
      val writes = Array.tabulate(3) { master =>
        reads(master).zipWithIndex.map { case (addr, index) =>
          Write(addr, BigInt(master * 256 + index), BigInt(index % 16))
        }
      }
      def target(master: Int, addr: BigInt, write: Boolean): Option[Int] = {
        if (addr % 4 != 0) None
        else
          regions.indices.find { index =>
            val region = regions(index)
            addr >= region.base && addr < region.end && config.canAccess(master, index) &&
            (if (write) region.writable else region.readable)
          }
      }
      def local(index: Int, addr: BigInt): BigInt          = addr - regions(index).base + regions(index).localBase
      def readResult(index: Int, addr: BigInt): ReadResult = ReadResult((BigInt(index + 1) << 24) | addr, false)
      def writeError(index: Int, addr: BigInt, data: BigInt, strobe: BigInt): Boolean =
        ((addr ^ data ^ strobe ^ index) & 1) != 0

      simulate(new AcornCrossbar(config)) { dut =>
        dut.io.ins.foreach(initMaster)
        dut.io.outs.foreach(initSlave)
        reset(dut)
        val random         = new Random(0xac07L + depth)
        val readSent       = Array.fill(3)(0)
        val writeSent      = Array.fill(3)(0)
        val readReceived   = Array.fill(3)(0)
        val writeReceived  = Array.fill(3)(0)
        val expectedReads  = Array.fill(3)(mutable.Queue.empty[ReadResult])
        val expectedWrites = Array.fill(3)(mutable.Queue.empty[Boolean])
        val pendingReads   = Array.fill(3)(mutable.Queue.empty[PendingRead])
        val pendingWrites  = Array.fill(3)(mutable.Queue.empty[PendingWrite])
        val heldReads      = Array.fill[Option[ReadResult]](3)(None)
        val heldWrites     = Array.fill[Option[Boolean]](3)(None)
        var parallelReads  = false
        var cycle          = 0

        while ((readReceived.exists(_ < 24) || writeReceived.exists(_ < 24)) && cycle < 3000) {
          for (master <- 0 until 3) {
            val port = dut.io.ins(master)
            port.rd.cmd.valid #= (readSent(master) < 24).B
            if (readSent(master) < 24) port.rd.cmd.bits.addr #= reads(master)(readSent(master)).U
            port.wr.cmd.valid #= (writeSent(master) < 24).B
            if (writeSent(master) < 24) {
              val command = writes(master)(writeSent(master))
              port.wr.cmd.bits.addr #= command.addr.U
              port.wr.cmd.bits.data #= command.data.U
              port.wr.cmd.bits.strobe #= command.strobe.U
            }
            port.rd.rsp.ready #= (cycle > 2400 || random.nextInt(4) != 0).B
            port.wr.rsp.ready #= (cycle > 2400 || random.nextInt(3) != 0).B
          }
          for (index <- regions.indices) {
            val port = dut.io.outs(index)
            port.rd.cmd.ready #= (pendingReads(index).size < 8 && (cycle > 2400 || random.nextBoolean())).B
            port.wr.cmd.ready #= (pendingWrites(index).size < 8 && (cycle > 2400 || random.nextBoolean())).B
            val read = pendingReads(index).headOption.filter(_.due <= cycle)
            port.rd.rsp.valid #= read.nonEmpty.B
            port.rd.rsp.bits.data #= read.map(_.result.data).getOrElse(BigInt(0)).U
            port.rd.rsp.bits.error #= read.exists(_.result.error).B
            val write = pendingWrites(index).headOption.filter(_.due <= cycle)
            port.wr.rsp.valid #= write.nonEmpty.B
            port.wr.rsp.bits.error #= write.exists(_.error).B
          }

          // Retire responses before adding this cycle's new expectations; no zero-latency route bypass exists.
          for (master <- 0 until 3) {
            val port = dut.io.ins(master)
            if (bit(port.rd.rsp.valid)) {
              val actual = ReadResult(number(port.rd.rsp.bits.data), bit(port.rd.rsp.bits.error))
              expectedReads(master).nonEmpty shouldBe true
              actual shouldBe expectedReads(master).front
              heldReads(master).foreach(_ shouldBe actual)
              if (bit(port.rd.rsp.ready)) {
                expectedReads(master).dequeue()
                readReceived(master) += 1
                heldReads(master) = None
              } else heldReads(master) = Some(actual)
            } else heldReads(master) shouldBe None
            if (bit(port.wr.rsp.valid)) {
              val actual = bit(port.wr.rsp.bits.error)
              expectedWrites(master).nonEmpty shouldBe true
              actual shouldBe expectedWrites(master).front
              heldWrites(master).foreach(_ shouldBe actual)
              if (bit(port.wr.rsp.ready)) {
                expectedWrites(master).dequeue()
                writeReceived(master) += 1
                heldWrites(master) = None
              } else heldWrites(master) = Some(actual)
            } else heldWrites(master) shouldBe None
          }
          for (index <- regions.indices) {
            val port = dut.io.outs(index)
            if (bit(port.rd.rsp.valid) && bit(port.rd.rsp.ready)) pendingReads(index).dequeue()
            if (bit(port.wr.rsp.valid) && bit(port.wr.rsp.ready)) pendingWrites(index).dequeue()
          }

          val acceptedReads  = mutable.Map.empty[Int, BigInt]
          val acceptedWrites = mutable.Map.empty[Int, Write]
          for (master <- 0 until 3) {
            val port = dut.io.ins(master)
            if (bit(port.rd.cmd.valid) && bit(port.rd.cmd.ready)) {
              val addr        = reads(master)(readSent(master))
              val destination = target(master, addr, write = false)
              expectedReads(master).enqueue(
                destination
                  .map { index =>
                    acceptedReads.contains(index) shouldBe false
                    val address = local(index, addr)
                    acceptedReads(index) = address
                    readResult(index, address)
                  }
                  .getOrElse(ReadResult(0, true))
              )
              readSent(master) += 1
            }
            if (bit(port.wr.cmd.valid) && bit(port.wr.cmd.ready)) {
              val command     = writes(master)(writeSent(master))
              val destination = target(master, command.addr, write = true)
              expectedWrites(master).enqueue(
                destination
                  .map { index =>
                    acceptedWrites.contains(index) shouldBe false
                    val translated = command.copy(addr = local(index, command.addr))
                    acceptedWrites(index) = translated
                    writeError(index, translated.addr, translated.data, translated.strobe)
                  }
                  .getOrElse(true)
              )
              writeSent(master) += 1
            }
          }
          if (acceptedReads.size >= 2) parallelReads = true
          for (index <- regions.indices) {
            val port = dut.io.outs(index)
            (bit(port.rd.cmd.valid) && bit(port.rd.cmd.ready)) shouldBe acceptedReads.contains(index)
            acceptedReads.get(index).foreach { addr =>
              port.rd.cmd.bits.addr expect addr.U
              pendingReads(index).enqueue(
                PendingRead(cycle + 2 + index * 3 + random.nextInt(4), readResult(index, addr))
              )
            }
            (bit(port.wr.cmd.valid) && bit(port.wr.cmd.ready)) shouldBe acceptedWrites.contains(index)
            acceptedWrites.get(index).foreach { command =>
              port.wr.cmd.bits.addr expect command.addr.U
              port.wr.cmd.bits.data expect command.data.U
              port.wr.cmd.bits.strobe expect command.strobe.U
              pendingWrites(index).enqueue(
                PendingWrite(
                  cycle + 1 + random.nextInt(4),
                  writeError(index, command.addr, command.data, command.strobe),
                )
              )
            }
          }
          dut.clock.step()
          cycle += 1
        }
        readReceived.toSeq shouldBe Seq(24, 24, 24)
        writeReceived.toSeq shouldBe Seq(24, 24, 24)
        expectedReads.foreach(_ shouldBe empty)
        expectedWrites.foreach(_ shouldBe empty)
        pendingReads.foreach(_ shouldBe empty)
        pendingWrites.foreach(_ shouldBe empty)
        parallelReads shouldBe true
      }
    }
  }
}
