package chipmunk
package sram

import chisel3._

/** SRAM with dual read/write ports. The two ports have independent clocks.
  *
  * @param config
  *   [[SramConfig]].
  */
class Sram2rw(val config: SramConfig) extends RawModule {
  val io = IO(new Bundle {
    val clock0 = Input(Clock())
    val clock1 = Input(Clock())
    val rw0    = Slave(new SramReadWriteIO(config))
    val rw1    = Slave(new SramReadWriteIO(config))
  })

  config.mc.family match {
    case "ram2rw" =>
      val uSram = Module(new Sram2rwWrapper(config))
      uSram.io.rw0_clock := io.clock0
      uSram.io.rw1_clock := io.clock1
      uSram.io.rw0 <> io.rw0
      uSram.io.rw1 <> io.rw1
    case _ =>
      throw new Exception(f"Cannot construct Sram2rw with memory family '${config.mc.family}'.")
  }
}
