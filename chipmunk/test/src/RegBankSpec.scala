package chipmunk.test

import chipmunk.regbank.{RegElementConfig, _}
import chipmunk.tester._
import chisel3._

class RegBankSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "RegBank" should "fail to compile when element addresses conflict" in {
    a[IllegalArgumentException] should be thrownBy {
      val regsConfig =
        Seq(
          RegElementConfig("R1", addr = 0, bitCount = 16),
          RegElementConfig("R2", addr = 2, bitCount = 16),
          RegElementConfig("R3", addr = 2, bitCount = 16)
        )
      compile(new RegBank(addrWidth = 8, dataWidth = 16, regs = regsConfig))
    }
  }

  it should "fail to compile when field bit ranges overlap" in {
    a[IllegalArgumentException] should be thrownBy {
      val regsConfig =
        Seq(
          RegElementConfig(
            "R1",
            addr = 0,
            fields = Seq(
              RegFieldConfig("F1", baseOffset = 0, bitCount = 16),
              RegFieldConfig("F2", baseOffset = 8, bitCount = 16)
            )
          )
        )
      compile(new RegBank(addrWidth = 8, dataWidth = 16, regs = regsConfig))
    }
  }

  it should "allow field access by either frontdoor or backdoor" in {
    val regsConfig =
      Seq(
        RegElementConfig("R1", addr = 0, bitCount = 16, initValue = 0x1234.U),
        RegElementConfig(
          "R2",
          addr = 1,
          fields = Seq(
            RegFieldConfig("F1", baseOffset = 0, bitCount = 8, initValue = 0x56.U, backdoorUpdate = true),
            RegFieldConfig("F2", baseOffset = 8, bitCount = 8, initValue = 0x78.U)
          )
        )
      )
    val compiled = compile(new RegBank(addrWidth = 8, dataWidth = 16, regs = regsConfig))
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true
      dut.clock.step()
      dut.reset #= false
      dut.clock.step()
      dut.io.access.wr.resp.ready #= true
      dut.io.access.rd.resp.ready #= true
      dut.io.access.wr.cmd.bits.wmask #= 0x3.U
      dut.io.fields("R1").value expect 0x1234.U
      dut.io.fields("R2_F1").value expect 0x56.U
      dut.io.fields("R2_F2").value expect 0x78.U
      dut.io.fields("R2_F1").backdoorUpdate.get.valid #= true
      dut.io.fields("R2_F1").backdoorUpdate.get.bits #= 0xab.U
      dut.clock.step()
      dut.io.fields("R2_F1").backdoorUpdate.get.valid #= false
      dut.io.access.wr.cmd.valid #= true
      dut.io.access.wr.cmd.bits.addr #= 1.U
      dut.io.access.wr.cmd.bits.wdata #= 0x1234.U
      dut.io.fields("R2_F1").value expect 0xab.U
      dut.clock.step()
      dut.io.access.wr.cmd.valid #= false
      dut.io.access.rd.cmd.valid #= true
      dut.io.access.rd.cmd.bits.addr #= 1.U
      dut.io.fields("R2_F1").value expect 0x34.U
      dut.io.fields("R2_F2").value expect 0x12.U
      dut.clock.step()
      dut.io.access.rd.resp.bits.rdata expect 0x1234.U
    }
  }

  it should "allow field frontdoor writing with mask" in {
    val regsConfig =
      Seq(RegElementConfig("R1", addr = 0, bitCount = 16, initValue = 0x1234.U))
    val compiled = compile(new RegBank(addrWidth = 8, dataWidth = 16, regs = regsConfig))
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true
      dut.clock.step()
      dut.reset #= false
      dut.clock.step()
      dut.io.access.wr.resp.ready #= true
      dut.io.access.rd.resp.ready #= true
      dut.io.access.wr.cmd.valid #= true
      dut.io.access.wr.cmd.bits.addr #= 0.U
      dut.io.access.wr.cmd.bits.wmask #= 0x1.U
      dut.io.access.wr.cmd.bits.wdata #= 0x5678.U
      dut.io.fields("R1").value expect 0x1234.U
      dut.clock.step()
      dut.io.fields("R1").value expect 0x1278.U
    }
  }

  it should "update field values according to access types" in {
    val regsConfig =
      Seq(
        RegElementConfig("R1", addr = 0, bitCount = 16, accessType = RegFieldAccessType.WriteSetReadClear),
        RegElementConfig("R2", addr = 1, bitCount = 16, accessType = RegFieldAccessType.WriteOneToggle),
        RegElementConfig("R3", addr = 2, bitCount = 16, accessType = RegFieldAccessType.ReadOnly)
      )
    val compiled = compile(new RegBank(addrWidth = 8, dataWidth = 16, regs = regsConfig))
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true
      dut.clock.step()
      dut.reset #= false
      dut.clock.step()
      dut.io.access.wr.resp.ready #= true
      dut.io.access.rd.resp.ready #= true
      dut.io.access.wr.cmd.valid #= true
      dut.io.access.wr.cmd.bits.addr #= 0.U
      dut.io.access.wr.cmd.bits.wmask #= 0x1.U
      dut.io.access.wr.cmd.bits.wdata #= 0x1234.U
      dut.io.fields("R1").value expect 0.U
      dut.clock.step()
      dut.io.access.wr.cmd.valid #= false
      dut.io.access.rd.cmd.valid #= true
      dut.io.access.rd.cmd.bits.addr #= 0.U
      dut.io.fields("R1").value expect 0x00ff.U
      dut.clock.step()
      dut.io.access.rd.cmd.valid #= false
      dut.io.access.wr.cmd.valid #= true
      dut.io.access.wr.cmd.bits.addr #= 1.U
      dut.io.access.wr.cmd.bits.wmask #= 0x3.U
      dut.io.access.wr.cmd.bits.wdata #= 0x5555.U
      dut.io.fields("R2").value expect 0.U
      dut.clock.step()
      dut.io.access.wr.cmd.bits.addr #= 2.U
      dut.io.access.wr.cmd.bits.wdata #= 0x5555.U
      dut.io.fields("R2").value expect 0x5555.U
      dut.clock.step()
      dut.io.fields("R3").value expect 0.U
    }
  }
}
