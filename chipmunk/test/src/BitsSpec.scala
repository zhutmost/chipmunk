package chipmunk.test

import chipmunk._
import chisel3._

class BitsSpec extends ChipmunkFlatSpec {
  "Bits" should "have extended methods" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val in         = Input(UInt(4.W))
        val lsBit      = Output(Bool())
        val msBit      = Output(Bool())
        val lsBits     = Output(UInt(2.W))
        val msBits     = Output(UInt(2.W))
        val isOneHot   = Output(Bool())
        val allOnes    = Output(UInt(4.W))
        val allZeros   = Output(UInt(4.W))
        val filledLsb  = Output(UInt(4.W))
        val assignBits = Output(UInt(4.W))
      })
      io.lsBit     := io.in.lsBit
      io.msBit     := io.in.msBit
      io.lsBits    := io.in.lsBits(2)
      io.msBits    := io.in.msBits(2)
      io.isOneHot  := io.in.isOneHot
      io.allOnes   := io.in.filledOnes
      io.allZeros  := io.in.filledZeros
      io.filledLsb := io.in.filledWith(io.in(0))
      when(io.lsBit) {
        io.assignBits.assignOnes()
      }.otherwise {
        io.assignBits.assignZeros()
      }
    }) { dut =>
      dut.io.in poke 0b0101.U
      dut.io.lsBit expect true.B
      dut.io.msBit expect false.B
      dut.io.lsBits expect 1.U
      dut.io.msBits expect 1.U
      dut.io.isOneHot expect false.B
      dut.io.allOnes expect 0b1111.U
      dut.io.allZeros expect 0.U
      dut.io.filledLsb expect 0b1111.U
      dut.io.assignBits expect 0b1111.U
      dut.clock.step()
      dut.io.in poke 0b1000.U
      dut.io.lsBit expect false.B
      dut.io.msBit expect true.B
      dut.io.lsBits expect 0.U
      dut.io.msBits expect 2.U
      dut.io.isOneHot expect true.B
      dut.io.allOnes expect 0b1111.U
      dut.io.allZeros expect 0.U
      dut.io.filledLsb expect 0b0000.U
      dut.io.assignBits expect 0b0000.U
    }
  }
}
