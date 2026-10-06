package chipmunk.test.spi

import scala.collection.mutable

import chisel3.*
import circt.stage.ChiselStage

import chipmunk.*
import chipmunk.acorn.{AcornIO, AcornParams}
import chipmunk.spi.{SpiDebugger, SpiIO}
import chipmunk.spi.SpiDebugger.{Registers, Status}
import chipmunk.test.ChipmunkFlatSpec

private object SpiDebuggerSpecDut {
  // The module clock only advances simulation time; the debugger uses a separately driven system clock.
  final class Harness(cpol: Boolean, cpha: Boolean, addressWidth: Int = 32, hasMisoValid: Boolean = true)
      extends Module {
    val io = IO(new Bundle {
      val systemLevel = Input(Bool())
      val spi         = Slave(new SpiIO(hasMisoValid))
      val bus         = Master(new AcornIO(AcornParams(32, addressWidth)))
    })
    private val debugger = withClock(io.systemLevel.asClock) {
      Module(new SpiDebugger(hasMisoValid, cpol, cpha, addressWidth))
    }
    debugger.io.sSpi :<>= io.spi
    io.bus :<>= debugger.io.mDbg
  }
}

final class SpiDebuggerSpec extends ChipmunkFlatSpec {
  private case class Request(read: Boolean, address: BigInt, data: BigInt, strobe: BigInt)
  private case class Response(due: Int, data: BigInt, error: Boolean)

  private final class Driver(dut: SpiDebuggerSpecDut.Harness, cpol: Boolean, cpha: Boolean) {
    // Four logical ticks per system period allow quarter-period SCK phase sweeps.
    private val systemPeriod                             = 4
    private var time, cycle                              = 0
    private var resetting                                = false
    private val reads                                    = mutable.Queue.empty[Response]
    private val writes                                   = mutable.Queue.empty[Response]
    private var heldRead, heldWrite                      = Option.empty[Seq[BigInt]]
    val requests                                         = mutable.ArrayBuffer.empty[Request]
    val memory                                           = mutable.Map.empty[BigInt, BigInt]
    var readReady, writeReady, returnReads, returnWrites = true
    var zeroCycle                                        = false
    var responseDelay                                    = 2
    var readError, writeError                            = false
    var responseCount                                    = 0
    var idleTicks, activeTicks                           = 16 // 8:1 system/SCK frequency ratio, 50% duty cycle.

    private def bit(signal: Bool): Boolean    = signal.peek().litToBoolean
    private def number(signal: UInt): BigInt  = signal.peek().litValue
    def expectedRead(address: BigInt): BigInt =
      memory.getOrElse(address, BigInt("81234567", 16) ^ (address & BigInt("ffffffff", 16)))

    dut.io.systemLevel #= false.B
    dut.io.spi.ssn #= true.B
    dut.io.spi.sck #= cpol.B
    dut.io.spi.mosi #= false.B
    reset()

    private def systemRise(): Unit = {
      dut.io.bus.rd.cmd.ready #= (readReady && !resetting).B
      dut.io.bus.wr.cmd.ready #= (writeReady && !resetting).B
      val readFire       = !resetting && readReady && bit(dut.io.bus.rd.cmd.valid)
      val writeFire      = !resetting && writeReady && bit(dut.io.bus.wr.cmd.valid)
      val readAddress    = number(dut.io.bus.rd.cmd.bits.addr)
      val writeAddress   = number(dut.io.bus.wr.cmd.bits.addr)
      val writeData      = number(dut.io.bus.wr.cmd.bits.data)
      val strobe         = number(dut.io.bus.wr.cmd.bits.strobe)
      val immediateRead  = zeroCycle && readFire && returnReads
      val immediateWrite = zeroCycle && writeFire && returnWrites
      val readValid      = !resetting && returnReads && (reads.headOption.exists(_.due <= cycle) || immediateRead)
      val writeValid     = !resetting && returnWrites && (writes.headOption.exists(_.due <= cycle) || immediateWrite)
      val rdResponse     = reads.headOption.getOrElse(Response(cycle, expectedRead(readAddress), readError))
      val wrResponse     = writes.headOption.getOrElse(Response(cycle, 0, writeError))
      dut.io.bus.rd.rsp.valid #= readValid.B
      dut.io.bus.rd.rsp.bits.data #= rdResponse.data.U
      dut.io.bus.rd.rsp.bits.error #= rdResponse.error.B
      dut.io.bus.wr.rsp.valid #= writeValid.B
      dut.io.bus.wr.rsp.bits.error #= wrResponse.error.B

      val rdPayload = Seq(readAddress)
      val wrPayload = Seq(writeAddress, writeData, strobe)
      heldRead.foreach { expected =>
        assert(bit(dut.io.bus.rd.cmd.valid) && rdPayload == expected, "Read request changed while stalled")
      }
      heldWrite.foreach { expected =>
        assert(bit(dut.io.bus.wr.cmd.valid) && wrPayload == expected, "Write request changed while stalled")
      }
      heldRead = Option.when(!resetting && bit(dut.io.bus.rd.cmd.valid) && !readReady)(rdPayload)
      heldWrite = Option.when(!resetting && bit(dut.io.bus.wr.cmd.valid) && !writeReady)(wrPayload)
      val readResponse  = readValid && bit(dut.io.bus.rd.rsp.ready)
      val writeResponse = writeValid && bit(dut.io.bus.wr.rsp.ready)

      dut.io.systemLevel #= true.B
      dut.clock.step(0)
      if (readResponse && reads.nonEmpty) reads.dequeue()
      if (writeResponse && writes.nonEmpty) writes.dequeue()
      if (readResponse) responseCount += 1
      if (writeResponse) responseCount += 1
      if (readFire) {
        requests += Request(true, readAddress, 0, 0)
        if (!immediateRead) reads.enqueue(Response(cycle + responseDelay, expectedRead(readAddress), readError))
      }
      if (writeFire) {
        requests += Request(false, writeAddress, writeData, strobe)
        var result = memory.getOrElse(writeAddress, BigInt(0))
        for (byte <- 0 until 4 if strobe.testBit(byte)) {
          val mask = BigInt(255) << (byte * 8)
          result = (result & ~mask) | (writeData & mask)
        }
        if (!writeError) memory(writeAddress) = result
        if (!immediateWrite) writes.enqueue(Response(cycle + responseDelay, 0, writeError))
      }
      cycle += 1
    }

    def tick(ticks: Int = 1): Unit = {
      for (_ <- 0 until ticks) {
        dut.clock.step(cycles = 1, period = 2)
        time += 1
        if (time % systemPeriod == 2) systemRise()
        else if (time % systemPeriod == 0) {
          dut.io.systemLevel #= false.B
          dut.clock.step(0)
        }
      }
    }

    def reset(): Unit = {
      resetting = true
      reads.clear()
      writes.clear()
      heldRead = None
      heldWrite = None
      dut.io.spi.ssn #= true.B
      dut.io.spi.sck #= cpol.B
      dut.reset #= true.B
      tick(12)
      dut.reset #= false.B
      resetting = false
      tick(12)
    }

    def begin(phase: Int = -1): Unit = {
      if (phase >= 0) tick((phase - time % systemPeriod + systemPeriod) % systemPeriod)
      dut.io.spi.ssn #= false.B
      dut.io.spi.misoValid.foreach(_ expect true.B)
      tick(16)
    }
    def end(): Unit = {
      dut.io.spi.ssn #= true.B
      dut.io.spi.misoValid.foreach(_ expect false.B)
      tick(16)
    }

    def transfer(data: BigInt, bits: Int): BigInt = {
      var result = BigInt(0)
      for (index <- (0 until bits).reverse) {
        dut.io.spi.mosi #= data.testBit(index).B
        if (!cpha) tick(idleTicks)
        dut.io.spi.sck #= (!cpol).B
        if (!cpha) result = (result << 1) | (if (bit(dut.io.spi.miso)) 1 else 0)
        tick(activeTicks)
        dut.io.spi.sck #= cpol.B
        if (cpha) {
          result = (result << 1) | (if (bit(dut.io.spi.miso)) 1 else 0)
          tick(idleTicks)
        }
      }
      result
    }
    def writeRegister(index: Int, data: BigInt, phase: Int = -1): Unit = {
      begin(phase)
      transfer(index, 8)
      transfer(data, 32)
      end()
    }
    def readRegister(index: Int, phase: Int = -1): BigInt = {
      begin(phase)
      transfer(0x40 | index, 8)
      val result = transfer(0, 32)
      end()
      result
    }
    def busWrite(address: BigInt, data: BigInt): Unit = {
      begin()
      transfer(0x80, 8)
      transfer(address, 32)
      transfer(data, 32)
      end()
    }
    def busRead(address: BigInt): BigInt = {
      begin()
      transfer(0xc0, 8)
      transfer(address, 32)
      transfer(0, 8)
      val result = transfer(0, 32)
      end()
      result
    }
  }

  for (cpol <- Seq(false, true); cpha <- Seq(false, true)) {
    s"SpiDebugger mode ${2 * (if (cpol) 1 else 0) + (if (cpha) 1 else 0)}" should
      "transfer complete words at 8:1 across system-clock phases and preserve register indices" in {
        simulate(new SpiDebuggerSpecDut.Harness(cpol, cpha, addressWidth = 45)) { dut =>
          val spi = new Driver(dut, cpol, cpha)
          assert(spi.readRegister(Registers.TEST) == BigInt("3f1b00e5", 16))
          assert(spi.readRegister(Registers.BUS_WR_MASK) == 15)
          val patterns = Seq("80000001", "13579bdf", "deadbeef", "ffffffff").map(BigInt(_, 16))
          for (phase <- 0 until 4) {
            spi.writeRegister(Registers.TEST, patterns(phase), phase)
            assert(spi.readRegister(Registers.TEST, phase) == patterns(phase))
            spi.writeRegister(Registers.BUS_ADDR_H, 0x123)
            spi.writeRegister(Registers.BUS_WR_MASK, 5)
            val address     = BigInt(0x100 + phase * 4)
            val wideAddress = (BigInt(0x123) << 32) | address
            spi.busWrite(address, patterns(phase))
            assert(spi.requests.last == Request(false, wideAddress, patterns(phase), 5))
            assert(spi.busRead(address) == spi.memory(wideAddress))
            assert(spi.requests.last == Request(true, wideAddress, 0, 0))
            assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
          }
          // Unequal half periods and a non-integral frequency ratio also sweep edge phase during the word.
          spi.idleTicks = 17
          spi.activeTicks = 19
          spi.writeRegister(Registers.TEST, BigInt("a55a8001", 16))
          assert(spi.readRegister(Registers.TEST) == BigInt("a55a8001", 16))
          assert(spi.responseCount == spi.requests.size)
        }
      }
  }

  "SpiDebugger" should "hold snapshots through stalls and reject triggers until the response completes" in {
    simulate(new SpiDebuggerSpecDut.Harness(false, true)) { dut =>
      val spi = new Driver(dut, false, true)
      spi.writeReady = false
      spi.returnWrites = false
      spi.busWrite(0x100, BigInt("fedcba98", 16))
      dut.io.bus.wr.cmd.valid expect true.B
      spi.writeRegister(Registers.BUS_ADDR_L, 0x240)
      spi.writeRegister(Registers.BUS_WR_DATA, 0x13579)
      spi.writeRegister(Registers.BUS_WR_MASK, 3)
      assert(spi.busRead(0x300) == SpiDebugger.earlyReadValue)
      assert(spi.readRegister(Registers.STATUS) == (Status.BUSY | Status.REJECTED | Status.EARLY_READ))
      assert(spi.requests.isEmpty)
      spi.writeReady = true
      spi.tick(32)
      assert(spi.requests.toSeq == Seq(Request(false, 0x100, BigInt("fedcba98", 16), 15)))
      dut.io.bus.wr.cmd.valid expect false.B
      spi.writeRegister(Registers.BUS_RD_RESP, 0)
      assert(spi.requests.size == 1)
      assert((spi.readRegister(Registers.STATUS) & Status.BUSY) != 0)
      spi.returnWrites = true
      spi.tick(32)
      assert(spi.readRegister(Registers.STATUS) == (Status.WR_DONE | Status.REJECTED | Status.EARLY_READ))
      spi.writeRegister(Registers.BUS_ADDR_L, 0x100)
      spi.writeRegister(Registers.BUS_RD_RESP, 0)
      assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
      assert(spi.readRegister(Registers.BUS_RD_DATA) == BigInt("fedcba98", 16))
      spi.writeRegister(Registers.BUS_WR_RESP, 0)
      assert(spi.requests.last == Request(false, 0x100, 0x13579, 3))
      assert(spi.readRegister(Registers.STATUS) == Status.WR_DONE)
    }
  }

  it should "return an EARLY_READ snapshot and retain a response arriving during SPI transmission" in {
    simulate(new SpiDebuggerSpecDut.Harness(true, false)) { dut =>
      val spi = new Driver(dut, true, false)
      spi.returnReads = false
      spi.begin()
      spi.transfer(0xc0, 8)
      spi.transfer(0x400, 32)
      spi.transfer(0, 8)
      val firstByte = spi.transfer(0, 8)
      spi.returnReads = true
      val remainder = spi.transfer(0, 24)
      spi.end()
      assert((firstByte << 24 | remainder) == SpiDebugger.earlyReadValue)
      assert(spi.readRegister(Registers.STATUS) == (Status.RD_DONE | Status.EARLY_READ))
      assert(spi.readRegister(Registers.BUS_RD_DATA) == spi.expectedRead(0x400))
      spi.memory(BigInt(0x404)) = SpiDebugger.earlyReadValue
      assert(spi.busRead(0x404) == SpiDebugger.earlyReadValue)
      assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
      assert(spi.requests.size == 2 && spi.responseCount == 2)
    }
  }

  it should "discard incomplete writes, ignore extra clocks and keep submitted reads across deselection" in {
    simulate(new SpiDebuggerSpecDut.Harness(false, false)) { dut =>
      val spi = new Driver(dut, false, false)
      for (bits <- Seq(0, 1, 7)) {
        spi.begin()
        spi.transfer(BigInt(Registers.TEST) >> (8 - bits), bits)
        spi.end()
      }
      for (bits <- Seq(0, 1, 15, 31)) {
        spi.begin()
        spi.transfer(Registers.TEST, 8)
        spi.transfer(BigInt("12345678", 16) >> (32 - bits), bits)
        spi.end()
        assert(spi.readRegister(Registers.TEST) == BigInt("3f1b00e5", 16))
      }
      for (bits <- Seq(0, 1, 31)) {
        spi.begin()
        spi.transfer(0x80, 8)
        spi.transfer(0x100, 32)
        spi.transfer(BigInt("ffffffff", 16) >> (32 - bits), bits)
        spi.end()
      }
      spi.begin()
      spi.transfer(0xc0, 8)
      spi.transfer(0x100 >> 1, 31)
      spi.end()
      assert(spi.requests.isEmpty)
      spi.begin()
      spi.transfer(0x80, 8)
      spi.transfer(0x100, 32)
      spi.transfer(BigInt("80000001", 16), 32)
      spi.transfer(0x80, 8)
      spi.transfer(0x200, 32)
      spi.transfer(0xffff, 32)
      spi.end()
      assert(spi.requests.toSeq == Seq(Request(false, 0x100, BigInt("80000001", 16), 15)))
      spi.returnReads = false
      spi.begin()
      spi.transfer(0xc0, 8)
      spi.transfer(0x100, 32)
      spi.end()
      assert(spi.requests.size == 2)
      assert(spi.readRegister(Registers.STATUS) == Status.BUSY)
      spi.returnReads = true
      spi.tick(32)
      assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
      assert(spi.readRegister(Registers.BUS_RD_DATA) == BigInt("80000001", 16))
    }
  }

  it should "complete bad addresses locally, preserve overflow checks and cancel pending state on reset" in {
    simulate(new SpiDebuggerSpecDut.Harness(false, false, addressWidth = 12)) { dut =>
      val spi = new Driver(dut, false, false)
      for (address <- Seq(3, 0x1000)) {
        assert(spi.busRead(address) == 0)
        assert(spi.readRegister(Registers.BUS_RD_RESP) == 1)
        assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
        spi.busWrite(address, 123)
        assert(spi.readRegister(Registers.BUS_WR_RESP) == 1)
        assert(spi.readRegister(Registers.STATUS) == Status.WR_DONE)
      }
      spi.writeRegister(Registers.BUS_ADDR_L, 0x1000)
      assert(spi.readRegister(Registers.BUS_ADDR_L) == 0)
      spi.writeRegister(Registers.BUS_WR_RESP, 1)
      assert(spi.readRegister(Registers.BUS_WR_RESP) == 1)
      assert(spi.requests.isEmpty)
      assert(spi.readRegister(Registers.BUS_ADDR_H) == 0)
      assert(spi.readRegister(8) == 0)
      spi.writeRegister(Registers.BUS_ADDR_L, 0x100)
      spi.writeRegister(Registers.BUS_WR_MASK, 0)
      spi.writeRegister(Registers.BUS_WR_RESP, 1)
      assert(spi.requests.last == Request(false, 0x100, 123, 0))
      spi.writeReady = false
      spi.busWrite(0x200, 9)
      dut.io.bus.wr.cmd.valid expect true.B
      spi.reset()
      dut.io.bus.wr.cmd.valid expect false.B
      assert(spi.readRegister(Registers.STATUS) == 0)
      assert(spi.readRegister(Registers.BUS_WR_MASK) == 15)
      assert(spi.readRegister(Registers.TEST) == BigInt("3f1b00e5", 16))
      spi.writeReady = true
      spi.busWrite(0x200, 9)
      assert(spi.requests.last == Request(false, 0x200, 9, 15))
      spi.readError = true
      assert(spi.busRead(0x200) == 0)
      assert(spi.readRegister(Registers.BUS_RD_RESP) == 1)
      spi.writeError = true
      spi.busWrite(0x200, 10)
      assert(spi.readRegister(Registers.BUS_WR_RESP) == 1)
    }
  }

  it should "reject upper-address overflow and accept the highest representable aligned address" in {
    simulate(new SpiDebuggerSpecDut.Harness(true, true, addressWidth = 45)) { dut =>
      val spi = new Driver(dut, true, true)
      spi.writeRegister(Registers.BUS_ADDR_H, 0x2000)
      spi.writeRegister(Registers.BUS_RD_RESP, 0)
      assert(spi.readRegister(Registers.BUS_RD_RESP) == 1)
      assert(spi.requests.isEmpty)
      assert(spi.busRead(0xfffffffcL) == 0)
      assert(spi.requests.isEmpty)
      spi.writeRegister(Registers.BUS_ADDR_H, 0x1fff)
      spi.writeRegister(Registers.BUS_ADDR_L, 0xfffffffcL)
      spi.writeRegister(Registers.BUS_RD_RESP, 0)
      assert(spi.requests.last.address == (BigInt(1) << 45) - 4)
      assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
    }
  }

  it should "handle same-cycle responses and 64-bit addresses without an optional MISO enable" in {
    simulate(new SpiDebuggerSpecDut.Harness(false, true, addressWidth = 64, hasMisoValid = false)) { dut =>
      val spi = new Driver(dut, false, true)
      spi.zeroCycle = true
      spi.writeRegister(Registers.BUS_ADDR_H, 0xffffffffL)
      spi.busWrite(0xfffffffcL, BigInt("01234567", 16))
      assert(spi.busRead(0xfffffffcL) == BigInt("01234567", 16))
      assert(spi.requests.forall(_.address == (BigInt(1) << 64) - 4))
      assert(spi.responseCount == 2)
      assert(spi.readRegister(Registers.STATUS) == Status.RD_DONE)
    }
  }

  it should "validate address widths and elaborate the minimum width" in {
    for (width <- Seq(0, 1, 65)) {
      intercept[IllegalArgumentException] { ChiselStage.emitCHIRRTL(new SpiDebugger(busAddrWidth = width)) }
    }
    assert(ChiselStage.emitCHIRRTL(new SpiDebugger(busAddrWidth = 2)).contains("module SpiDebugger"))
  }
}
