package chipmunk.test.regbank

import chisel3.*

import chipmunk.Slave
import chipmunk.acorn.*
import chipmunk.regbank.*
import chipmunk.regbank.RegFieldAccessType.*

private object RegBankSpecDut {
  class CrossbarHarness extends Module {
    val io = IO(new Bundle {
      val ins    = Vec(2, Slave(new AcornIO(16, 16)))
      val values = Output(Vec(2, UInt(16.W)))
    })
    private val crossbar = Module(
      new AcornCrossbar(AcornCrossbarConfig(AcornParams(16, 16), 2, Seq(AcornSlaveConfig("csr", 0x1000, 0x100))))
    )
    private val bank = Module(new RegBank(8, 16, Seq(RegElementConfig("A", 0, 16), RegElementConfig("B", 2, 16))))
    crossbar.io.ins <> io.ins
    bank.io.access <> crossbar.slave("csr")
    io.values(0) := bank.io.fields("A").value
    io.values(1) := bank.io.fields("B").value
  }
}

class RegBankSpec extends RegBankSpecSupport {
  "RegBank" should "map fields, preserve gaps, and mask writes across byte boundaries" in {
    val config = RegBankConfig(
      AcornParams(16, 8),
      Seq(
        RegElementConfig(
          "R",
          2,
          Seq(
            RegFieldConfig("HIGH", 12, 4, initValue = 0xd, accessType = ReadOnly),
            RegFieldConfig("LOW", 4, 8, initValue = 0xab, backdoorUpdate = true),
          ),
        ),
        RegElementConfig("S", 0, 16, initValue = 0x1234),
      ),
    )
    simulate(new RegBank(config)) { dut =>
      init(dut)
      dut.io.fields("S").value expect 0x1234.U
      dut.io.fields("S_UNNAMED").value expect 0x1234.U
      read(dut, 2, 0xdab0)
      write(dut, 2, 0x1234, strobe = 1)
      dut.io.fields("R_LOW").value expect 0xa3.U
      read(dut, 2, 0xda30)
      write(dut, 2, 0x5600, strobe = 2)
      dut.io.fields("R_LOW").value expect 0x63.U
      read(dut, 2, 0xd630)
      val update = dut.io.fields("R_LOW").backdoorUpdate.get
      update.valid #= true.B
      update.bits #= 0x89.U
      dut.clock.step()
      update.valid #= false.B
      read(dut, 2, 0xd890)
    }
  }

  it should "implement all built-in access rules with byte masks and read side effects" in {
    case class Rule(access: RegFieldAccessType, partial: Int, full: Int, afterRead: Int, readable: Boolean = true)
    val rules = Seq(
      Rule(ReadOnly, 0x55aa, 0x55aa, 0x55aa),
      Rule(ReadWrite, 0x5533, 0xff00, 0xff00),
      Rule(ReadClear, 0x55aa, 0x55aa, 0),
      Rule(ReadSet, 0x55aa, 0x55aa, 0xffff),
      Rule(WriteReadClear, 0x5533, 0xff00, 0),
      Rule(WriteReadSet, 0x5533, 0xff00, 0xffff),
      Rule(WriteClear, 0x5500, 0, 0),
      Rule(WriteSet, 0x55ff, 0xffff, 0xffff),
      Rule(WriteSetReadClear, 0x55ff, 0xffff, 0),
      Rule(WriteClearReadSet, 0x5500, 0, 0xffff),
      Rule(WriteOneClear, 0x5588, 0x0088, 0x0088),
      Rule(WriteOneSet, 0x55bb, 0xffbb, 0xffbb),
      Rule(WriteOneToggle, 0x5599, 0xaa99, 0xaa99),
      Rule(WriteZeroClear, 0x5522, 0x5500, 0x5500),
      Rule(WriteZeroSet, 0x55ee, 0x55ff, 0x55ff),
      Rule(WriteZeroToggle, 0x5566, 0x5599, 0x5599),
      Rule(WriteOneSetReadClear, 0x55bb, 0xffbb, 0),
      Rule(WriteOneClearReadSet, 0x5588, 0x0088, 0xffff),
      Rule(WriteZeroSetReadClear, 0x55ee, 0x55ff, 0),
      Rule(WriteZeroClearReadSet, 0x5522, 0x5500, 0xffff),
      Rule(WriteOnly, 0x5533, 0xff00, 0xff00, readable = false),
      Rule(WriteOnlyClear, 0x5500, 0, 0, readable = false),
      Rule(WriteOnlySet, 0x55ff, 0xffff, 0xffff, readable = false),
      Rule(WriteOnce, 0x5533, 0x5533, 0x5533),
      Rule(WriteOnlyOnce, 0x5533, 0x5533, 0x5533, readable = false),
    )
    val regs = rules.zipWithIndex.map { case (rule, index) =>
      RegElementConfig(s"R$index", 2 * index, 16, initValue = 0x55aa, accessType = rule.access)
    }
    simulate(new RegBank(8, 16, regs)) { dut =>
      init(dut)
      for ((rule, index) <- rules.zipWithIndex) {
        val field = dut.io.fields(s"R$index")
        write(dut, 2 * index, 0xffff, strobe = 0)
        field.value expect 0x55aa.U
        write(dut, 2 * index, 0x0f33, strobe = 1)
        field.value expect rule.partial.U
        write(dut, 2 * index, 0xff00)
        field.value expect rule.full.U
        read(dut, 2 * index, if (rule.readable) rule.full else 0)
        field.value expect rule.afterRead.U
        read(dut, 2 * index, if (rule.readable) rule.afterRead else 0)
      }
    }
  }

  it should "unlock both write-once variants on reset" in {
    val regs = Seq(
      RegElementConfig("R", 0, 16, accessType = WriteOnce),
      RegElementConfig("W", 2, 16, accessType = WriteOnlyOnce),
    )
    simulate(new RegBank(8, 16, regs)) { dut =>
      init(dut)
      for ((name, addr) <- Seq("R" -> 0, "W" -> 2)) {
        write(dut, addr, 0x55aa)
        write(dut, addr, 0xaa55)
        dut.io.fields(name).value expect 0x55aa.U
      }
      reset(dut)
      for ((name, addr) <- Seq("R" -> 0, "W" -> 2)) {
        dut.io.fields(name).value expect 0.U
        write(dut, addr, 0xaa55)
        dut.io.fields(name).value expect 0xaa55.U
      }
    }
  }

  it should "exclude zero masks and locked writes from collision arbitration" in {
    val regs = Seq(
      RegElementConfig(
        "RC",
        0,
        16,
        initValue = 5,
        accessType = WriteReadClear,
        collisionMode = RegFieldCollisionMode.WriteReadBackdoor,
      ),
      RegElementConfig(
        "HW",
        2,
        16,
        initValue = 5,
        backdoorUpdate = true,
        collisionMode = RegFieldCollisionMode.WriteReadBackdoor,
      ),
      RegElementConfig(
        "ONCE",
        4,
        16,
        accessType = WriteOnce,
        backdoorUpdate = true,
        collisionMode = RegFieldCollisionMode.WriteReadBackdoor,
      ),
    )
    simulate(new RegBank(8, 16, regs)) { dut =>
      init(dut)
      val port = dut.io.access
      port.rd.cmd.valid #= true.B
      port.rd.cmd.bits.addr #= 0.U
      port.wr.cmd.valid #= true.B
      port.wr.cmd.bits.addr #= 0.U
      port.wr.cmd.bits.strobe #= 0.U
      dut.io.fields("RC").isBeingWritten expect false.B
      dut.clock.step()
      port.rd.cmd.valid #= false.B
      port.wr.cmd.valid #= false.B
      port.rd.rsp.bits.data expect 5.U
      dut.io.fields("RC").value expect 0.U
      dut.clock.step()
      val hardware = dut.io.fields("HW").backdoorUpdate.get
      hardware.valid #= true.B
      hardware.bits #= 0x5a.U
      port.wr.cmd.valid #= true.B
      port.wr.cmd.bits.addr #= 2.U
      dut.clock.step()
      port.wr.cmd.valid #= false.B
      hardware.valid #= false.B
      dut.io.fields("HW").value expect 0x5a.U
      dut.clock.step()
      write(dut, 4, 0x5555)
      val once = dut.io.fields("ONCE").backdoorUpdate.get
      once.valid #= true.B
      once.bits #= 0xabcd.U
      port.wr.cmd.valid #= true.B
      port.wr.cmd.bits.addr #= 4.U
      port.wr.cmd.bits.data #= 0xaaaa.U
      port.wr.cmd.bits.strobe #= 3.U
      dut.clock.step()
      port.wr.cmd.valid #= false.B
      once.valid #= false.B
      dut.io.fields("ONCE").value expect 0xabcd.U
      dut.clock.step()
    }
  }

  it should "honor all six collision priorities while returning the previous read value" in {
    val modes = Seq(
      RegFieldCollisionMode.WriteReadBackdoor -> 7,
      RegFieldCollisionMode.WriteBackdoorRead -> 7,
      RegFieldCollisionMode.ReadBackdoorWrite -> 0,
      RegFieldCollisionMode.ReadWriteBackdoor -> 0,
      RegFieldCollisionMode.BackdoorWriteRead -> 9,
      RegFieldCollisionMode.BackdoorReadWrite -> 9,
    )
    val regs = modes.zipWithIndex.map { case ((mode, _), index) =>
      RegElementConfig(
        s"R$index",
        2 * index,
        16,
        initValue = 5,
        accessType = WriteReadClear,
        collisionMode = mode,
        backdoorUpdate = true,
      )
    }
    simulate(new RegBank(8, 16, regs)) { dut =>
      init(dut)
      val port = dut.io.access
      for (((_, expected), index) <- modes.zipWithIndex) {
        val field  = dut.io.fields(s"R$index")
        val update = field.backdoorUpdate.get
        update.valid #= true.B
        update.bits #= 9.U
        port.rd.cmd.valid #= true.B
        port.rd.cmd.bits.addr #= (2 * index).U
        port.wr.cmd.valid #= true.B
        port.wr.cmd.bits.addr #= (2 * index).U
        port.wr.cmd.bits.data #= 7.U
        port.wr.cmd.bits.strobe #= 3.U
        dut.clock.step()
        update.valid #= false.B
        port.rd.cmd.valid #= false.B
        port.wr.cmd.valid #= false.B
        port.rd.rsp.bits.data expect 5.U
        field.value expect expected.U
        dut.clock.step()
      }
    }
  }

  it should "hold response snapshots under backpressure and execute side effects once" in {
    val regs = Seq(
      RegElementConfig("RC", 0, 16, initValue = 0x1234, accessType = ReadClear, backdoorUpdate = true),
      RegElementConfig("RW", 2, 16),
    )
    simulate(new RegBank(8, 16, regs)) { dut =>
      init(dut)
      val port = dut.io.access
      port.rd.rsp.ready #= false.B
      port.wr.rsp.ready #= false.B
      port.rd.cmd.valid #= true.B
      port.rd.cmd.bits.addr #= 0.U
      port.wr.cmd.valid #= true.B
      port.wr.cmd.bits.addr #= 2.U
      port.wr.cmd.bits.data #= 0x5678.U
      port.wr.cmd.bits.strobe #= 3.U
      dut.clock.step()
      port.rd.cmd.bits.addr #= 2.U
      port.wr.cmd.bits.addr #= 1.U
      dut.io.fields("RC").value expect 0.U
      dut.io.fields("RW").value expect 0x5678.U
      val hardware = dut.io.fields("RC").backdoorUpdate.get
      hardware.valid #= true.B
      hardware.bits #= 0xcafe.U
      dut.clock.step()
      hardware.valid #= false.B
      for (_ <- 0 until 3) {
        port.rd.cmd.ready expect false.B
        port.wr.cmd.ready expect false.B
        port.rd.rsp.valid expect true.B
        port.rd.rsp.bits.data expect 0x1234.U
        port.rd.rsp.bits.error expect false.B
        port.wr.rsp.valid expect true.B
        port.wr.rsp.bits.error expect false.B
        dut.io.fields("RC").value expect 0xcafe.U
        dut.clock.step()
      }
      port.rd.rsp.ready #= true.B
      port.wr.rsp.ready #= true.B
      port.rd.cmd.ready expect true.B
      port.wr.cmd.ready expect true.B
      dut.clock.step()
      port.rd.cmd.valid #= false.B
      port.wr.cmd.valid #= false.B
      port.rd.rsp.bits.data expect 0x5678.U
      port.rd.rsp.bits.error expect false.B
      port.wr.rsp.bits.error expect true.B
      dut.clock.step()
      port.rd.rsp.valid expect false.B
      port.wr.rsp.valid expect false.B
      read(dut, 6, 0, error = true)
      read(dut, 1, 0, error = true)
      write(dut, 6, 0xffff, error = true)
      dut.io.fields("RW").value expect 0x5678.U
      dut.io.fields("RC").value expect 0xcafe.U
    }
  }

  it should "accept one read and write per cycle and read the previous value" in {
    simulate(new RegBank(8, 16, Seq(RegElementConfig("R", 0, 16)))) { dut =>
      init(dut)
      val port = dut.io.access
      port.rd.cmd.valid #= true.B
      port.wr.cmd.valid #= true.B
      port.wr.cmd.bits.strobe #= 3.U
      for (data <- 1 to 8) {
        port.wr.cmd.bits.data #= data.U
        port.rd.cmd.ready expect true.B
        port.wr.cmd.ready expect true.B
        dut.clock.step()
        port.rd.rsp.valid expect true.B
        port.rd.rsp.bits.data expect (data - 1).U
        port.wr.rsp.valid expect true.B
        dut.io.fields("R").value expect data.U
      }
      port.rd.cmd.valid #= false.B
      port.wr.cmd.valid #= false.B
      dut.clock.step()
      port.rd.rsp.valid expect false.B
      port.wr.rsp.valid expect false.B
    }
  }

  it should "serve two masters through AcornCrossbar with local byte offsets" in {
    simulate(new RegBankSpecDut.CrossbarHarness) { dut =>
      dut.io.ins.foreach(initMaster)
      reset(dut)
      val writeSent = Array.fill(2)(false)
      val writeDone = Array.fill(2)(false)
      for (cycle <- 0 until 16) {
        for (master <- 0 until 2) {
          val port = dut.io.ins(master)
          port.wr.cmd.valid #= (!writeSent(master)).B
          port.wr.cmd.bits.addr #= (0x1000 + 2 * master).U
          port.wr.cmd.bits.data #= (0x1234 + master).U
          port.wr.cmd.bits.strobe #= 3.U
          port.wr.rsp.ready #= (cycle > 3).B
        }
        for (master <- 0 until 2) {
          val port = dut.io.ins(master)
          if (port.wr.cmd.ready.peek().litToBoolean && !writeSent(master)) writeSent(master) = true
          if (port.wr.rsp.valid.peek().litToBoolean && cycle > 3) {
            writeDone(master) shouldBe false
            port.wr.rsp.bits.error expect false.B
            writeDone(master) = true
          }
        }
        dut.clock.step()
      }
      writeSent.toSeq shouldBe Seq(true, true)
      writeDone.toSeq shouldBe Seq(true, true)
      dut.io.values(0) expect 0x1234.U
      dut.io.values(1) expect 0x1235.U
      val readSent = Array.fill(2)(false)
      val readDone = Array.fill(2)(false)
      for (cycle <- 0 until 16) {
        for (master <- 0 until 2) {
          val port = dut.io.ins(master)
          port.rd.cmd.valid #= (!readSent(master)).B
          port.rd.cmd.bits.addr #= (0x1002 - 2 * master).U
          port.rd.rsp.ready #= (cycle > 3).B
        }
        for (master <- 0 until 2) {
          val port = dut.io.ins(master)
          if (port.rd.cmd.ready.peek().litToBoolean && !readSent(master)) readSent(master) = true
          if (port.rd.rsp.valid.peek().litToBoolean && cycle > 3) {
            readDone(master) shouldBe false
            port.rd.rsp.bits.data expect (0x1235 - master).U
            port.rd.rsp.bits.error expect false.B
            readDone(master) = true
          }
        }
        dut.clock.step()
      }
      readSent.toSeq shouldBe Seq(true, true)
      readDone.toSeq shouldBe Seq(true, true)
    }
  }
}
