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

class TestRunnerUtilsSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "TestRunnerUtils" should "peek or poke signal values of DUT ports" in {
    compile(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val b = Output(UInt(3.W))
        val c = Input(Vec(2, SInt(3.W)))
        val d = Output(Vec(2, UInt(3.W)))
      })
      io.b := io.a.asUInt
      io.d.zip(io.c).foreach { case (o, i) => o := i.asUInt }
    }).runSim { dut =>
      import TestRunnerUtils._
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.io.b.getValue() shouldBe svsim.Simulation.Value(3, 7)
      dut.io.b expect 7
      dut.io.b expect 7.U
      dut.io.c(0) #= 1
      dut.io.c(1) #= 2.S(3.W)
      dut.io.d(0) expect 1
      dut.io.d(1) expect 2.U
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
