package chipmunk.test

import chipmunk.tester._
import chisel3._

class TestRunnerSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  val compiled = compileTester(new Module {
    val io = IO(new Bundle {
      val a = Input(UInt(3.W))
      val b = Output(UInt(3.W))
    })
    io.b := io.a
  })
  "TestRunner" should "compile DUT and run simulation" in {
    val success = compiled.runSim { module =>
      val dut   = module.wrapped
      val clock = module.port(dut.clock)
      val reset = module.port(dut.reset)
      reset.set(1)
      clock.tick(timestepsPerPhase = 1, maxCycles = 10, inPhaseValue = 1, outOfPhaseValue = 0, sentinel = None)
      reset.set(0)
      val a = module.port(dut.io.a)
      val b = module.port(dut.io.b)
      a #= 3
      assert(b.get().asBigInt == 3)
      assert(b.get().bitCount == 3)
      clock.tick(timestepsPerPhase = 1, maxCycles = 10, inPhaseValue = 1, outOfPhaseValue = 0, sentinel = None)
    }
    assert(success)
  }
}
