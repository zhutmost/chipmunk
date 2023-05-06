package chipmunk
package sram

import chisel3._
import chisel3.util._

/** SRAM with one read-only port and one write-only port. The two ports have independent clocks.
  *
  * @param c
  *   [[SramConfig]].
  */
class Sram1r1w(c: SramConfig) extends RawModule {
  require(c.mc.family != "ram1rw")

  val io = IO(new Bundle {
    val clockR = Input(Clock())
    val clockW = Input(Clock())
    val r      = Slave(new SramReadIO(c))
    val w      = Slave(new SramWriteIO(c))
  })

  if (c.mc.family == "ram2rw") {
    val uSram = Module(new Sram2rwWrapper(c))
    uSram.io.rw0_clock   := io.clockW
    uSram.io.rw1_clock   := io.clockR
    uSram.io.rw0.enable  := io.w.enable
    uSram.io.rw0.address := io.w.address
    uSram.io.rw0.dataIn  := io.w.dataIn
    uSram.io.rw0.write   := true.B
    uSram.io.rw1.enable  := io.r.enable
    uSram.io.rw1.address := io.r.address
    uSram.io.rw1.write   := false.B
    uSram.io.rw1.dataIn  := 0.U
    io.r.dataOut         := uSram.io.rw1.dataOut
    if (c.hasMask) {
      uSram.io.rw0.mask.get := io.w.mask.get
      uSram.io.rw1.mask.get := 0.U
    }
  } else {
    val uSram = Module(new Sram1r1wWrapper(c))
    uSram.io.wr_clock := io.clockW
    uSram.io.rd_clock := io.clockR
    uSram.io.wr <> io.w
    uSram.io.rd <> io.r
  }
}
