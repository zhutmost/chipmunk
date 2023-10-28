package chipmunk.test

import chipmunk.tester._
import chisel3._

import java.nio.file.Paths

class TestRunnerSimpleSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "TestRunner" should "compile a DUT and run its testbench" in {
    val compiled = TestRunnerConfig(withWaveform = true).compile(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val b = Output(SInt(3.W))
      })
      io.b := io.a
    })
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.clock.step()
      dut.io.b expect -1
    }
    val workingDirName  = f"${compiled.workspace.workingDirectoryPrefix}-runSim-1"
    val vcdWaveformPath = Paths.get(compiled.workspace.absolutePath, workingDirName, "trace.vcd")
    vcdWaveformPath.toFile.exists() shouldBe true
  }

  it should "compile another DUT and run another testbench" in {
    val compiled = compile(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val c = Output(UInt(3.W))
      })
      io.c := io.a.asUInt
    })
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.clock.step()
      dut.io.c expect 7
    }
  }
}

class TestRunnerCompileFailSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "TestRunner" should "throw Exception when DUT compilation fails" in {
    a[ChiselException] shouldBe thrownBy {
      compile(new Module {
        val io = IO(new Bundle {
          val a = Input(SInt(3.W))
          val b = Output(UInt(3.W))
        })
        io.b := io.a // Type mismatch
      })
    }
  }
}
