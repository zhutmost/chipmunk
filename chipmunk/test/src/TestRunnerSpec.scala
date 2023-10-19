package chipmunk.test

import chipmunk.tester._
import chisel3._

class TestRunnerSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  val compiled = TestRunnerConfig(withWaveform = true).compile(new Module {
    val io = IO(new Bundle {
      val a = Input(SInt(3.W))
      val b = Output(SInt(3.W))
      val c = Output(UInt(3.W))
    })
    io.b := io.a
    io.c := io.a.asUInt
  })
  "TestRunner" should "compile DUT and run simulation" in {
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.clock.step()
      dut.io.b expect -1
      dut.io.c expect 7
    }
  }
}
