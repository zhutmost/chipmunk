package chipmunk
package amba

import stream.{Stream, StreamIO}

import chisel3._

private[amba] trait HasAxiId extends Bundle {
  val idWidth: Int
  val id = if (idWidth > 0) Some(UInt(idWidth.W)) else None
}

class AxiWriteAddrChannel(
  addrWidth: Int,
  val idWidth: Int,
  val lenWidth: Int = 8,
  val lockWidth: Int = 1,
  hasQos: Boolean = false,
  hasRegion: Boolean = false
) extends AxiLiteAddrChannel(addrWidth)
    with HasAxiId {
  require(List(4, 8) contains lenWidth, "Bit width of AxLEN can only be 4 (AXI3) or 8 (AXI4).")
  require(List(1, 2) contains lockWidth, "Bit width of AxLOCK can only be 2 (AXI3) or 1 (AXI4).")

  val size   = AxiBurstSize()
  val len    = UInt(lenWidth.W)
  val burst  = AxiBurstType()
  val cache  = UInt(4.W)
  val lock   = UInt(lockWidth.W)
  val qos    = if (hasQos) Some(UInt(4.W)) else None
  val region = if (hasRegion) Some(UInt(4.W)) else None

  def cacheBufferable: Bool     = cache(0).asBool
  def cacheModifiable: Bool     = cache(1).asBool
  def cacheOtherAllocated: Bool = cache(2).asBool
  def cacheAllocated: Bool      = cache(3).asBool
}

class AxiReadAddrChannel(
  addrWidth: Int,
  idWidth: Int,
  lenWidth: Int = 8,
  lockWidth: Int = 1,
  hasQos: Boolean = false,
  hasRegion: Boolean = false
) extends AxiWriteAddrChannel(addrWidth, idWidth, lenWidth, lockWidth, hasQos, hasRegion) {
  override def cacheAllocated: Bool      = cache(2).asBool
  override def cacheOtherAllocated: Bool = cache(3).asBool
}

class AxiWriteDataChannel(dataWidth: Int, val idWidth: Int = 0)
    extends AxiLiteWriteDataChannel(dataWidth)
    with HasAxiId {
  val last = Bool()
}

class AxiWriteRespChannel(val idWidth: Int) extends AxiLiteWriteRespChannel() with HasAxiId

class AxiReadDataChannel(dataWidth: Int, val idWidth: Int) extends AxiLiteReadDataChannel(dataWidth) with HasAxiId {
  val last = Bool()
}

private[amba] abstract class AxiIOBase(val dataWidth: Int, val addrWidth: Int, val idWidth: Int)
    extends Bundle
    with IsMasterSlave {
  override def isMaster = true

  def allowedDataWidth = List(8, 16, 32, 64, 128, 256, 512, 1024)
  require(allowedDataWidth contains dataWidth, s"Data width can only be 8, 16, ..., or 1024, but got $dataWidth")

  require(addrWidth > 0, s"Address width must be at least 1, but got $addrWidth")
  require(idWidth >= 0, s"ID width of AXI bus must be at least 0, but got $idWidth")

  val dataWidthByteNum: Int = dataWidth / 8
  val hasId: Boolean        = idWidth > 0

  val aw: StreamIO[AxiWriteAddrChannel]
  val w: StreamIO[AxiWriteDataChannel]
  val b: StreamIO[AxiWriteRespChannel]
  val ar: StreamIO[AxiReadAddrChannel]
  val r: StreamIO[AxiReadDataChannel]
}

/** AMBA4 AXI IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data.
  * @param addrWidth
  *   The bit width of the bus address.
  * @param idWidth
  *   The bit width of the bus id.
  * @param hasQos
  *   Whether the bus has QoS signals.
  * @param hasRegion
  *   Whether the bus has region signals.
  */
class Axi4IO(dataWidth: Int, addrWidth: Int, idWidth: Int, hasQos: Boolean = false, hasRegion: Boolean = false)
    extends AxiIOBase(dataWidth, addrWidth, idWidth) {
  val aw = Master(Stream(new AxiWriteAddrChannel(addrWidth, idWidth, hasQos = hasQos, hasRegion = hasRegion)))
  val w  = Master(Stream(new AxiWriteDataChannel(dataWidth))) // AXI4 does NOT have WID.
  val b  = Slave(Stream(new AxiWriteRespChannel(idWidth)))
  val ar = Master(Stream(new AxiReadAddrChannel(addrWidth, idWidth, hasQos = hasQos, hasRegion = hasRegion)))
  val r  = Slave(Stream(new AxiReadDataChannel(dataWidth, idWidth)))
}

/** AMBA3 AXI IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data.
  * @param addrWidth
  *   The bit width of the bus address.
  * @param idWidth
  *   The bit width of the bus id.
  */
class Axi3IO(dataWidth: Int, addrWidth: Int, idWidth: Int) extends AxiIOBase(dataWidth, addrWidth, idWidth) {
  val aw = Master(Stream(new AxiWriteAddrChannel(addrWidth, idWidth, lenWidth = 4, lockWidth = 2)))
  val w  = Master(Stream(new AxiWriteDataChannel(dataWidth, idWidth)))
  val b  = Slave(Stream(new AxiWriteRespChannel(idWidth)))
  val ar = Master(Stream(new AxiReadAddrChannel(addrWidth, idWidth, lenWidth = 4, lockWidth = 2)))
  val r  = Slave(Stream(new AxiReadDataChannel(dataWidth, idWidth)))
}

type AxiIO = Axi4IO
