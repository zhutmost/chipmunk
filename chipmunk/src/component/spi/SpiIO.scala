package chipmunk
package component.spi

import chisel3._

/** Four-wire SPI IO.
  *
  * @param hasMisoValid
  *   whether the MISO signal has a valid signal for tri-state IO-cell control. Note that only the Slave-side SPI may
  *   have this signal.
  */
class SpiIO(val hasMisoValid: Boolean = false) extends Bundle with IsMasterSlave {

  val sck       = Output(Bool())
  val ssn       = Output(Bool())
  val mosi      = Output(Bool())
  val miso      = Input(Bool())
  val misoValid = if (hasMisoValid) Some(Input(Bool())) else None

  def isMaster = true
}
