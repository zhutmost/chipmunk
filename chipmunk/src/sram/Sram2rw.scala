package chipmunk
package sram

import chisel3._
import chisel3.util._

/** SRAM with dual read/write ports. The two ports have independent clocks.
  *
  * @param c
  *   [[SramConfig]].
  */
class Sram2rw(c: SramConfig) extends RawModule {
  require(c.mc.family == "ram2rw")

  val io = IO(new Bundle {
    val clock0 = Input(Clock())
    val clock1 = Input(Clock())
    val rw0    = Slave(new SramReadWriteIO(c))
    val rw1    = Slave(new SramReadWriteIO(c))
  })

  val uSram = Module(new Sram2rwWrapper(c))

  uSram.io.rw0_clock := io.clock0
  uSram.io.rw1_clock := io.clock1
  uSram.io.rw0 <> io.rw0
  uSram.io.rw1 <> io.rw1
}
