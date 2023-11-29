package chipmunk.test

import chipmunk.RegNegNext
import chipmunk.tester._
import chisel3._

class RegNegSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "RegNeg" should "latch data at falling clock edge" in {
    val compiled = compile(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(3.W))
        val b = Output(UInt(3.W))
      })
      val r = RegNegNext(io.a, init = 0.U)
      io.b := r
    })
    compiled.runSim { dut =>
      import TestRunnerUtils._
      // expected waveform:
      // input  clk:  10 10 10 10 01 10
      // input  rst:  xx 1. __ __ __ __
      // input    a:  xx 1. .. 2. 3. ..
      // output   b:  0. .. 1. .2 .. .3
      dut.clock.stepNeg()
      dut.io.b expect 0.U
      dut.io.a #= 1.U
      dut.reset #= true.B
      dut.clock.stepNeg()
      dut.io.b expect 0.U
      dut.reset #= false.B
      dut.clock.stepNeg()
      dut.io.b expect 1.U
      dut.io.a #= 2.U
      dut.clock.stepNeg()
      dut.io.b expect 2.U
      dut.io.a #= 3.U
      dut.clock.step()
      dut.io.b expect 2.U // does NOT latch data at rising clock edge
      dut.clock.stepNeg()
      dut.io.b expect 3.U
    }
  }

  it should "accept bundled data type" in {
    compile(new Module {
      val io = IO(new Bundle {
        val a = Input(Vec(2, UInt(3.W)))
        val b = Output(Vec(2, UInt(3.W)))
      })
      val r = RegNegNext(io.a)
      io.b := r
    }).runSim { dut =>
      import TestRunnerUtils._
      dut.io.a(0) #= 1.U
      dut.io.a(1) #= 2.U
      dut.clock.stepNeg()
      dut.io.b(0) expect 1.U
      dut.io.b(1) expect 2.U
      dut.io.a(1) #= 3.U
      dut.clock.stepNeg()
      dut.io.b(1) expect 3.U
    }
  }
}
