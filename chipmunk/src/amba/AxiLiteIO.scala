package chipmunk
package amba

import stream.{Stream, StreamIO}

import chisel3._

class AxiLiteAddrChannel(val addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W)

  def protPrivileged: Bool  = prot(0).asBool
  def protNonsecure: Bool   = prot(1).asBool
  def protInstruction: Bool = prot(2).asBool
}

class AxiLiteWriteDataChannel(val dataWidth: Int) extends Bundle {
  val strobeWidth: Int = dataWidth / 8

  val data = UInt(dataWidth.W)
  val strb = UInt(strobeWidth.W)
}

class AxiLiteWriteRespChannel() extends Bundle {
  val resp = AxiResp()
}

class AxiLiteReadDataChannel(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val resp = AxiResp()
}

private[amba] abstract class AxiLiteIOBase(val dataWidth: Int, val addrWidth: Int) extends Bundle with IsMasterSlave {
  override def isMaster = true

  def allowedDataWidth: List[Int] = List(32, 64)

  // Do not check dataWidth, because many implementations violate this.
  // require(
  //   allowedDataWidth contains dataWidth,
  //   s"Data width can only be 32 or 64, but got $dataWidth"
  // )

  require(addrWidth > 0, s"Address width must be at least 1, but got $addrWidth")

  val dataWidthByteNum: Int = dataWidth / 8

  val aw: StreamIO[AxiLiteAddrChannel]
  val w: StreamIO[AxiLiteWriteDataChannel]
  val b: StreamIO[AxiLiteWriteRespChannel]
  val ar: StreamIO[AxiLiteAddrChannel]
  val r: StreamIO[AxiLiteReadDataChannel]
}

/** AMBA4 AXI-Lite IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data. It can only be 64 or 32.
  * @param addrWidth
  *   The bit width of the bus address.
  */
class Axi4LiteIO(dataWidth: Int, addrWidth: Int) extends AxiLiteIOBase(dataWidth, addrWidth) {
  val aw = Master(Stream(new AxiLiteAddrChannel(addrWidth)))
  val w  = Master(Stream(new AxiLiteWriteDataChannel(dataWidth)))
  val b  = Slave(Stream(new AxiLiteWriteRespChannel()))
  val ar = Master(Stream(new AxiLiteAddrChannel(addrWidth)))
  val r  = Slave(Stream(new AxiLiteReadDataChannel(dataWidth)))
}
