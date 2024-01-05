package chipmunk
package amba

import stream.Stream
import chisel3._

private[amba] trait HasAxiId extends Bundle {
  val idWidth: Int
  val id = if (idWidth > 0) Some(UInt(idWidth.W)) else None
}

class AxiWriteAddrChannel(addrWidth: Int, val idWidth: Int, val lenWidth: Int = 8, val lockWidth: Int = 1)
    extends AxiLiteWriteAddrChannel(addrWidth)
    with HasAxiId {
  require(List(4, 8) contains lenWidth, "Bit width of AxLEN can only be 4 (AXI3) or 8 (AXI4).")
  require(List(1, 2) contains lockWidth, "Bit width of AxLOCK can only be 2 (AXI3) or 1 (AXI4).")

  val size  = AxiBurstSize()
  val len   = UInt(lenWidth.W)
  val burst = AxiBurstType()
  val cache = UInt(4.W)
  val lock  = UInt(lockWidth.W)
  val qos   = UInt(4.W)

  def cacheBufferable: Bool     = cache(0).asBool
  def cacheModifiable: Bool     = cache(1).asBool
  def cacheAllocated: Bool      = cache(2).asBool
  def cacheOtherAllocated: Bool = cache(3).asBool
}

class AxiWriteDataChannel(dataWidth: Int, val idWidth: Int = 0)
    extends AxiLiteWriteDataChannel(dataWidth)
    with HasAxiId {
  val last = Bool()
}

class AxiWriteRespChannel(val idWidth: Int) extends AxiLiteWriteRespChannel() with HasAxiId

class AxiReadAddrChannel(addrWidth: Int, idWidth: Int, lenWidth: Int = 8, lockWidth: Int = 1)
    extends AxiWriteAddrChannel(addrWidth, idWidth, lenWidth, lockWidth)

class AxiReadDataChannel(dataWidth: Int, val idWidth: Int) extends AxiLiteReadDataChannel(dataWidth) with HasAxiId {
  val last = Bool()
}

private[amba] abstract class AxiBase(dataWidth: Int, addrWidth: Int, val idWidth: Int)
    extends AxiLiteBase(dataWidth, addrWidth) {
  override def allowedDataWidth = List(8, 16, 32, 64, 128, 256, 512, 1024)

  require(idWidth >= 0, s"ID width of AXI bus must be at least 0, but got $idWidth")

  val hasId: Boolean = idWidth > 0
}

/** AMBA AXI4 IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data.
  * @param addrWidth
  *   The bit width of the bus address.
  * @param idWidth
  *   The bit width of the bus id.
  */
class Axi4IO(dataWidth: Int, addrWidth: Int, idWidth: Int) extends AxiBase(dataWidth, addrWidth, idWidth) {
  val aw = Master(Stream(new AxiWriteAddrChannel(addrWidth, idWidth)))
  val w  = Master(Stream(new AxiWriteDataChannel(dataWidth))) // AXI4 does NOT have WID.
  val b  = Slave(Stream(new AxiWriteRespChannel(idWidth)))
  val ar = Master(Stream(new AxiReadAddrChannel(addrWidth, idWidth)))
  val r  = Slave(Stream(new AxiReadDataChannel(dataWidth, idWidth)))
}

/** AMBA AXI3 IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data.
  * @param addrWidth
  *   The bit width of the bus address.
  * @param idWidth
  *   The bit width of the bus id.
  */
class Axi3IO(dataWidth: Int, addrWidth: Int, idWidth: Int) extends AxiBase(dataWidth, addrWidth, idWidth) {
  val aw = Master(Stream(new AxiWriteAddrChannel(addrWidth, idWidth, lenWidth = 4, lockWidth = 2)))
  val w  = Master(Stream(new AxiWriteDataChannel(dataWidth, idWidth)))
  val b  = Slave(Stream(new AxiWriteRespChannel(idWidth)))
  val ar = Master(Stream(new AxiReadAddrChannel(addrWidth, idWidth, lenWidth = 4, lockWidth = 2)))
  val r  = Slave(Stream(new AxiReadDataChannel(dataWidth, idWidth)))
}
