package chipmunk
package sram

import chisel3._

/** SRAM with a single read/write port.
  *
  * @param config
  *   [[SramConfig]].
  */
class Sram1rw(val config: SramConfig) extends RawModule {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val rw    = Slave(new SramReadWriteIO(config))
  })

  config.mc.family match {
    case "ram1rw" =>
      val uSram = Module(new Sram1rwWrapper(config))
      uSram.io.rw_clock := io.clock
      uSram.io.rw <> io.rw
    case "ram1r1w" =>
      val uSram = Module(new Sram1r1wWrapper(config))
      uSram.io.wr_clock   := io.clock
      uSram.io.rd_clock   := io.clock
      uSram.io.wr.address := io.rw.address
      uSram.io.wr.enable  := io.rw.enable && io.rw.write
      uSram.io.wr.dataIn  := io.rw.dataIn
      if (config.hasMask) {
        uSram.io.wr.mask.get := io.rw.mask.get
      }
      uSram.io.rd.address := io.rw.address
      uSram.io.rd.enable  := io.rw.enable && !io.rw.write
      io.rw.dataOut       := uSram.io.rd.dataOut
    case "ram2rw" =>
      val uSram = Module(new Sram2rwWrapper(config))
      uSram.io.rw0_clock := io.clock
      uSram.io.rw1_clock := io.clock
      uSram.io.rw0 <> io.rw
      uSram.io.rw1.enable  := false.B
      uSram.io.rw1.write   := false.B
      uSram.io.rw1.address := 0.U
      uSram.io.rw1.dataIn  := 0.U
      if (config.hasMask) {
        uSram.io.rw1.mask.get := 0.U
      }
  }
}
