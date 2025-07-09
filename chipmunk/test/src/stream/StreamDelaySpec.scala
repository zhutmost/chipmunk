package chipmunk.test
package stream

import chipmunk._
import chipmunk.stream._
import chisel3._

class StreamDelaySpec extends ChipmunkFlatSpec {
  "StreamDelay" should "delay the input stream by fixed cycles" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val in  = Slave(Stream(UInt(8.W)))
        val out = Master(Stream(UInt(8.W)))
      })
      io.out << io.in.delayFixed(cycles = 2)
    }) { dut =>
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
    simulate(new Module {
      val io = IO(new Bundle {
        val in  = Slave(Stream(UInt(8.W)))
        val out = Master(Stream(UInt(8.W)))
      })
      io.out << io.in.delayFixed(cycles = 0)
    }) { dut =>
      for (_ <- 0 until 10) {
        dut.io.in.valid.randomize()
        dut.io.in.bits.randomize()
        dut.io.out.ready #= true.B
        if (dut.io.in.valid.peekBoolean()) {
          dut.io.out.valid expect true
          dut.io.in.ready expect true
          dut.io.out.bits expect dut.io.in.bits.peek()
        }
        dut.clock.step()
      }
    }
  }

  it should "delay the input stream by random cycles" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val in  = Slave(Stream(UInt(8.W)))
        val out = Master(Stream(UInt(8.W)))
      })
      io.out << io.in.delayRandom(minCycles = 2, maxCycles = 8)
    }) { dut =>
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
          val outValid: Boolean = dut.io.out.valid.peekBoolean()
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
