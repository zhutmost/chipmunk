package chipmunk.test

import chipmunk.tester._
import chisel3._

class TestRunnerSpec extends ChipmunkFlatSpec {
  "TestRunner" should "compile DUT and run simulation" in {
    TestRunnerConfig(withWaveform = true).simulate(new Module {
      val io = IO(new Bundle {
        val a = Input(SInt(3.W))
        val b = Output(SInt(3.W))
        val c = Output(UInt(3.W))
      })
      io.b := io.a
      io.c := io.a.asUInt
    }) { dut =>
      import TestRunnerUtils._
      dut.clock.step()
      dut.io.a #= -1.S(3.W)
      dut.clock.step()
      dut.io.b expect -1
      dut.io.c expect 7
    }
  }
}
