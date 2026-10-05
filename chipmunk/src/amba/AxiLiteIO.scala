package chipmunk
package amba

import chisel3._

import stream.{Stream, StreamIO}

final case class AxiLiteParams(dataWidth: Int, addrWidth: Int) {
  require(dataWidth == 32 || dataWidth == 64, "AXI4-Lite data width must be 32 or 64.")
  require(addrWidth >= 1, "AXI4-Lite address width must be positive.")

  val strobeWidth: Int = dataWidth / 8
}

final class AxiLiteAddrChannel(val params: AxiLiteParams) extends Bundle {
  val addr = UInt(params.addrWidth.W)
  val prot = UInt(3.W)

  def protPrivileged: Bool  = prot(0).asBool
  def protNonsecure: Bool   = prot(1).asBool
  def protInstruction: Bool = prot(2).asBool
}

final class AxiLiteWriteDataChannel(val params: AxiLiteParams) extends Bundle {
  val data = UInt(params.dataWidth.W)
  val strb = UInt(params.strobeWidth.W)
}

final class AxiLiteWriteRespChannel(val params: AxiLiteParams) extends Bundle {
  val resp = AxiResp()
}

final class AxiLiteReadDataChannel(val params: AxiLiteParams) extends Bundle {
  val data = UInt(params.dataWidth.W)
  val resp = AxiResp()
}

/** AMBA4 AXI-Lite IO bundle. */
final class AxiLiteIO(val params: AxiLiteParams) extends Bundle with IsMasterSlave with HasAxiVerilogIO {
  def this(dataWidth: Int, addrWidth: Int) =
    this(AxiLiteParams(dataWidth, addrWidth))

  override def isMaster = true

  val aw = Master(Stream(new AxiLiteAddrChannel(params)))
  val w  = Master(Stream(new AxiLiteWriteDataChannel(params)))
  val b  = Slave(Stream(new AxiLiteWriteRespChannel(params)))
  val ar = Master(Stream(new AxiLiteAddrChannel(params)))
  val r  = Slave(Stream(new AxiLiteReadDataChannel(params)))
}
