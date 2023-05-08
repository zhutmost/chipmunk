package chipmunk
package sram

import chisel3._

/** SRAM with one read-only port and one write-only port. The two ports have independent clocks.
  *
  * @param config
  *   [[SramConfig]].
  */
class Sram1r1w(val config: SramConfig) extends RawModule {
  val io = IO(new Bundle {
    val clockR = Input(Clock())
    val clockW = Input(Clock())
    val r      = Slave(new SramReadIO(config))
    val w      = Slave(new SramWriteIO(config))
  })

  config.mc.family match {
    case "ram2rw" =>
      val uSram = Module(new Sram2rwWrapper(config))
      uSram.io.rw0_clock   := io.clockW
      uSram.io.rw0.enable  := io.w.enable
      uSram.io.rw0.write   := true.B
      uSram.io.rw0.address := io.w.address
      uSram.io.rw0.dataIn  := io.w.dataIn
      uSram.io.rw1_clock   := io.clockR
      uSram.io.rw1.enable  := io.r.enable
      uSram.io.rw1.write   := false.B
      uSram.io.rw1.address := io.r.address
      uSram.io.rw1.dataIn  := 0.U
      io.r.dataOut         := uSram.io.rw1.dataOut
      if (config.hasMask) {
        uSram.io.rw0.mask.get := io.w.mask.get
        uSram.io.rw1.mask.get := 0.U
      }
    case "ram1r1w" =>
      val uSram = Module(new Sram1r1wWrapper(config))
      uSram.io.wr_clock := io.clockW
      uSram.io.rd_clock := io.clockR
      uSram.io.wr <> io.w
      uSram.io.rd <> io.r
    case _ =>
      throw new Exception(f"Cannot construct Sram1r1w with memory family '${config.mc.family}'.")
  }
}
