package chipmunk
package amba

import chisel3._

private[amba] abstract class ApbIOBase(
  val dataWidth: Int,
  val addrWidth: Int,
  val hasProt: Boolean = false,
  val hasStrb: Boolean = false
) extends Bundle
    with IsMasterSlave {
  override def isMaster     = true
  val dataWidthByteNum: Int = dataWidth / 8

  def allowedDataWidth = List(8, 16, 32)
  require(allowedDataWidth contains dataWidth, s"Data width can only be 8, 16, or 32, but got $dataWidth.")

  require(addrWidth > 0, s"Address width must be at least 1, but got $addrWidth.")

  val addr   = Output(UInt(addrWidth.W))
  val selx   = Output(UInt(4.W))
  val enable = Output(Bool())
  val write  = Output(Bool())
  val wdata  = Output(UInt(dataWidth.W))
  val ready  = Input(Bool())
  val rdata  = Input(UInt(dataWidth.W))
  val slverr = Input(Bool())

  // The below signals are optional in APB4 but not in APB3
  val prot = if (hasProt) Some(Output(UInt(3.W))) else None
  val strb = if (hasStrb) Some(Output(UInt(dataWidthByteNum.W))) else None
}

/** AMBA3 APB IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data. It can only be 8, 16, or 32.
  * @param addrWidth
  *   The bit width of the bus address.
  */
class Apb3IO(dataWidth: Int, addrWidth: Int) extends ApbIOBase(dataWidth, addrWidth) {
  def rtlConnector(postfix: Option[String] = None, toggleCase: Boolean = false) = {
    new ApbIORtlConnector(dataWidth, addrWidth)(postfix, toggleCase)
  }
}

/** AMBA4 APB IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data. It can only be 8, 16, or 32.
  * @param addrWidth
  *   The bit width of the bus address.
  * @param hasProt
  *   Whether the bus has prot signals.
  * @param hasStrb
  *   Whether the bus has strobe signals.
  */
class Apb4IO(dataWidth: Int, addrWidth: Int, hasProt: Boolean = false, hasStrb: Boolean = false)
    extends ApbIOBase(dataWidth, addrWidth, hasProt, hasStrb) {
  def rtlConnector(postfix: Option[String] = None, toggleCase: Boolean = false) = {
    new ApbIORtlConnector(dataWidth, addrWidth, hasProt, hasStrb)(postfix, toggleCase)
  }
}
