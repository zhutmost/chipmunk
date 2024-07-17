package chipmunk.test
package stream

import chipmunk._
import chipmunk.stream._
import chipmunk.tester._
import chisel3._

class StreamDelaySpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "StreamDelay" should "delay the input stream by fixed cycles" in {
    compile(new Module {
      val io = IO(new Bundle {
        val in  = Slave(Stream(UInt(8.W)))
        val out = Master(Stream(UInt(8.W)))
      })
      io.out << io.in.delayFixed(cycles = 2)
    }).runSim { dut =>
      import TestRunnerUtils._
      // inValid  _ 1 1 1 _
      // inReady  _ _ _ 1 _
      // outValid _ _ _ 1 _
      // outReady 1 1 1 1 1
      dut.io.in.valid #= false.B
      dut.io.in.bits #= 0.U
      dut.io.out.ready #= true.B
      dut.clock.step()
      dut.io.in.valid #= true.B
      dut.io.in.bits #= 42.U
      dut.clock.step(2)
      dut.io.out.valid expect true
      dut.io.out.bits expect 42.U
      dut.io.in.valid #= false.B
      dut.io.in.bits #= 0.U
      dut.clock.step()
      dut.io.out.valid expect false
    }
  }

  it should "have no stall when delay cycle is 0" in {
    compile(new Module {
      val io = IO(new Bundle {
        val in  = Slave(Stream(UInt(8.W)))
        val out = Master(Stream(UInt(8.W)))
      })
      io.out << io.in.delayFixed(cycles = 0)
    }).runSim { dut =>
      import TestRunnerUtils._
      for (_ <- 0 until 10) {
        dut.io.in.valid.randomize()
        dut.io.in.bits.randomize()
        dut.io.out.ready #= true
        if (dut.io.in.valid.get().litToBoolean) {
          dut.io.out.valid expect true
          dut.io.in.ready expect true
          dut.io.out.bits expect dut.io.in.bits.get()
        }
        dut.clock.step()
      }
    }
  }

  it should "delay the input stream by random cycles" in {
    compile(new Module {
      val io = IO(new Bundle {
        val in  = Slave(Stream(UInt(8.W)))
        val out = Master(Stream(UInt(8.W)))
      })
      io.out << io.in.delayRandom(minCycles = 2, maxCycles = 8)
    }).runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true.B
      dut.clock.step(5)
      dut.reset #= false.B
      dut.clock.step()
      dut.io.in.valid #= false.B
      dut.io.in.bits #= 0.U
      dut.io.out.ready #= true.B
      dut.clock.step()
      for (i <- 0 until 10) {
        dut.io.in.valid #= true.B
        dut.io.in.bits #= i.U

        // The first 2 cycles should have no valid output
        for (_ <- 0 until 2) {
          dut.io.out.valid expect false
          dut.clock.step()
        }

        // The next 7 cycles should have one valid output
        var outValids: List[Boolean] = List()
        for (_ <- 0 until 7) {
          val outValid: Boolean = dut.io.out.valid.get().litToBoolean
          outValids = outValids :+ outValid
          if (outValid) {
            dut.io.out.bits expect i.U
            dut.io.in.valid #= false.B
            dut.io.in.bits #= 0.U
          }
          dut.clock.step()
        }
        assert(outValids.count(_ == true) == 1) // outValids is one-hot and has only one true
        dut.clock.step()
      }
    }
  }
}
