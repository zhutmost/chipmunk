package chipmunk.test

import chipmunk._
import chisel3._

class MuxPriorityDefaultSpec extends ChipmunkFlatSpec {
  "MuxPriorityDefault" should "return the same result as MuxPriority, or the default value if no sel signal is asserted" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val sel    = Input(UInt(3.W))
        val result = Output(Vec(4, UInt(2.W)))
      })
      val choices = Seq(1.U, 2.U, 3.U)
      io.result(0) := MuxPriorityDefault(io.sel, choices, default = 0.U)
      io.result(1) := MuxPriorityDefault(io.sel.asBools, choices, default = 0.U)
      io.result(2) := MuxPriorityDefault(
        Seq(io.sel(0).asBool -> 1.U, io.sel(1).asBool -> 2.U, io.sel(2).asBool -> 3.U),
        default = 0.U
      )
      io.result(3) := MuxPriority(io.sel, choices)
    }) { dut =>
      dut.io.sel poke 0.U
      dut.io.result(0) expect 0.U
      dut.io.result(1) expect 0.U
      dut.io.result(2) expect 0.U
      // dut.io.result(3) is undefined when no sel asserted
      dut.clock.step()
      dut.io.sel poke 0b010.U
      dut.io.result(0) expect 2.U
      dut.io.result(1) expect 2.U
      dut.io.result(2) expect 2.U
      dut.io.result(3) expect 2.U
      dut.clock.step()
      dut.io.sel poke 0b011.U
      dut.io.result(0) expect 1.U
      dut.io.result(1) expect 1.U
      dut.io.result(2) expect 1.U
      dut.io.result(3) expect 1.U
    }
  }
}
