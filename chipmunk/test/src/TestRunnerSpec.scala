package chipmunk.test

import chisel3._

class TestRunnerSimpleSpec extends ChipmunkFlatSpec {
  "TestRunner" should "compile a DUT and run its testbench to generate a waveform" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val b = Output(SInt(3.W))
      })
      io.b := io.a
    }) { dut =>
      enableWaves()
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.clock.step()
      dut.io.b expect -1
    }
//    val workingDirName  = f"${compiled.workspace.workingDirectoryPrefix}-runSim-1"
//    val vcdWaveformPath = Paths.get(compiled.workspace.absolutePath, workingDirName, "trace.vcd")
//    vcdWaveformPath.toFile.exists() shouldBe true
  }

  it should "compile another DUT and run another testbench in a ephemeral workspace" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val c = Output(UInt(3.W))
      })
      io.c := io.a.asUInt
    }) { dut =>
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.clock.step()
      dut.io.c expect 7
    }
  }
}

class TestRunnerUtilsSpec extends ChipmunkFlatSpec {
  "TestRunnerUtils" should "peek or poke signal values of DUT ports" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val b = Output(UInt(3.W))
        val c = Input(Vec(2, SInt(3.W)))
        val d = Output(Vec(2, UInt(3.W)))
      })
      io.b := io.a.asUInt
      io.d.zip(io.c).foreach { case (o, i) => o := i.asUInt }
    }) { dut =>
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.io.b.peekValue() shouldBe svsim.Simulation.Value(bitCount = 3, asBigInt = 7)
      dut.io.b expect 7
      dut.io.b expect 7.U
      dut.io.c(0) #= 1.S
      dut.io.c(1) #= 2.S(3.W)
      dut.io.d(0) expect 1
      dut.io.d(1) expect 2.U
    }
  }
}

class TestRunnerCompileFailSpec extends ChipmunkFlatSpec {
  "TestRunner" should "throw Exception when DUT compilation fails" in {
    a[ChiselException] shouldBe thrownBy {
      simulate(new Module {
        val io = IO(new Bundle {
          val a = Input(SInt(3.W))
          val b = Output(UInt(3.W))
        })
        io.b := io.a // Type mismatch
      }) { dut =>
        dut.clock.step()
      }
    }
  }
}
