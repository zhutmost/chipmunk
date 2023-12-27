package chipmunk
package component.spi

import chisel3._

class SpiIO extends Bundle with IsMasterSlave {
  val sck  = Output(Bool())
  val ssn  = Output(Bool())
  val mosi = Output(Bool())
  val miso = Input(Bool())

  def isMaster = true
}
