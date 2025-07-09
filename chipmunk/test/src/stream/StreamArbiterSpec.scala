package chipmunk.test
package stream

import chipmunk._
import chipmunk.stream._
import chisel3._

class StreamArbiterSpec extends ChipmunkFlatSpec {
  "StreamArbiter" should "decide which input stream to fire" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val in0   = Slave(Flow(UInt(8.W)))
        val in1   = Slave(Flow(UInt(8.W)))
        val outRR = Master(Stream(UInt(8.W)))
        val outLF = Master(Stream(UInt(8.W)))
      })
      io.outRR << StreamArbiter.roundRobin(Seq(io.in0.asStream, io.in1.asStream))
      io.outLF << StreamArbiter.lowerFirst(Seq(io.in0.asStream, io.in1.asStream))
    }) { dut =>
      dut.reset #= true.B
      dut.io.outRR.ready #= true.B
      dut.io.outLF.ready #= true.B
      dut.io.in0.valid #= false.B
      dut.io.in1.valid #= false.B
      dut.clock.step()
      dut.reset #= false.B
      dut.io.in0.valid #= true.B
      dut.io.in0.bits #= 10.U
      dut.io.in1.valid #= true.B
      dut.io.in1.bits #= 20.U
      dut.io.outRR.valid expect true.B
      dut.io.outRR.bits expect 20.U
      dut.io.outLF.valid expect true.B
      dut.io.outLF.bits expect 10.U
      dut.clock.step()
      dut.io.in0.bits #= 11.U
      dut.io.in1.bits #= 21.U
      dut.io.outRR.bits expect 11.U
      dut.io.outLF.bits expect 11.U
      dut.clock.step()
      dut.io.in0.bits #= 12.U
      dut.io.in1.bits #= 22.U
      dut.io.outRR.bits expect 22.U
      dut.io.outLF.bits expect 12.U
      dut.clock.step()
      dut.io.in0.bits #= 13.U
      dut.io.in1.bits #= 23.U
      dut.io.outRR.bits expect 13.U
      dut.io.outLF.bits expect 13.U
    }
  }
}
