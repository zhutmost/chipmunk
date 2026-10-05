package chipmunk
package amba

import chisel3._

import stream.{Stream, StreamIO}

final case class AxiParams(
  dataWidth: Int,
  addrWidth: Int,
  idWidth: Int = 0,
  hasQos: Boolean = false,
  hasRegion: Boolean = false,
) {
  require(
    dataWidth >= 8 && dataWidth <= 1024 && (dataWidth & (dataWidth - 1)) == 0,
    "AXI4 data width must be a power of two from 8 to 1024.",
  )
  require(addrWidth >= 1, "AXI4 address width must be positive.")
  require(idWidth >= 0, "AXI4 ID width must be nonnegative.")

  val strobeWidth: Int = dataWidth / 8
  val hasId: Boolean   = idWidth > 0

  /** AxSIZE encoding for a transfer using the full data width. */
  val fullSize: Int = Integer.numberOfTrailingZeros(strobeWidth)
}

class AxiWriteAddrChannel(val params: AxiParams) extends Bundle {
  val addr   = UInt(params.addrWidth.W)
  val size   = AxiBurstSize()
  val len    = UInt(8.W)
  val burst  = AxiBurstType()
  val id     = if (params.idWidth > 0) Some(UInt(params.idWidth.W)) else None
  val prot   = UInt(3.W)
  val cache  = UInt(4.W)
  val lock   = UInt(1.W)
  val qos    = if (params.hasQos) Some(UInt(4.W)) else None
  val region = if (params.hasRegion) Some(UInt(4.W)) else None

  def protPrivileged: Bool  = prot(0).asBool
  def protNonsecure: Bool   = prot(1).asBool
  def protInstruction: Bool = prot(2).asBool

  def cacheBufferable: Bool     = cache(0).asBool
  def cacheModifiable: Bool     = cache(1).asBool
  def cacheOtherAllocated: Bool = cache(2).asBool
  def cacheAllocated: Bool      = cache(3).asBool

  def lockNormal: Bool    = lock === 0.U
  def lockExclusive: Bool = lock === 1.U
}

final class AxiReadAddrChannel(params: AxiParams) extends AxiWriteAddrChannel(params) {
  override def cacheAllocated: Bool      = cache(2).asBool
  override def cacheOtherAllocated: Bool = cache(3).asBool
}

final class AxiWriteDataChannel(val params: AxiParams) extends Bundle {
  val data = UInt(params.dataWidth.W)
  val strb = UInt(params.strobeWidth.W)
  val last = Bool()
}

final class AxiWriteRespChannel(val params: AxiParams) extends Bundle {
  val resp = AxiResp()
  val id   = if (params.idWidth > 0) Some(UInt(params.idWidth.W)) else None
}

final class AxiReadDataChannel(val params: AxiParams) extends Bundle {
  val data = UInt(params.dataWidth.W)
  val resp = AxiResp()
  val last = Bool()
  val id   = if (params.idWidth > 0) Some(UInt(params.idWidth.W)) else None
}

/** AMBA4 AXI IO bundle. */
final class AxiIO(val params: AxiParams) extends Bundle with IsMasterSlave with HasAxiVerilogIO {
  def this(dataWidth: Int, addrWidth: Int, idWidth: Int = 0, hasQos: Boolean = false, hasRegion: Boolean = false) =
    this(AxiParams(dataWidth, addrWidth, idWidth, hasQos, hasRegion))

  override def isMaster = true

  val aw = Master(Stream(new AxiWriteAddrChannel(params)))
  val w  = Master(Stream(new AxiWriteDataChannel(params)))
  val b  = Slave(Stream(new AxiWriteRespChannel(params)))
  val ar = Master(Stream(new AxiReadAddrChannel(params)))
  val r  = Slave(Stream(new AxiReadDataChannel(params)))
}
