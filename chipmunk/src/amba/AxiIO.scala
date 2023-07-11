package chipmunk
package amba

import chipmunk.stream
import chisel3._

object AxiBurstType extends ChiselEnum {
  val BURST_FIXED = Value(0.U)
  val BURST_INCR  = Value(1.U)
  val BURST_WRAP  = Value(2.U)
}

object AxiBurstSize extends ChiselEnum {
  val SIZE1   = Value(0.U)
  val SIZE2   = Value(1.U)
  val SIZE4   = Value(2.U)
  val SIZE8   = Value(3.U)
  val SIZE16  = Value(4.U)
  val SIZE32  = Value(5.U)
  val SIZE64  = Value(6.U)
  val SIZE128 = Value(7.U)
}

object AxiResp extends ChiselEnum {
  val RESP_OKAY   = Value(0.U)
  val RESP_EXOKAY = Value(1.U)
  val RESP_SLVERR = Value(2.U)
  val RESP_DECERR = Value(3.U)
}

trait HasAxiId extends Bundle {
  val idWidth: Int
  val id = if (idWidth > 0) Some(UInt(idWidth.W)) else None
}

class AxiLiteWriteAddrChannel(val addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W)
}

class AxiLiteWriteDataChannel(val dataWidth: Int) extends Bundle {
  val strobeWidth: Int = dataWidth / 8

  val data = UInt(dataWidth.W)
  val strb = UInt(strobeWidth.W)
}

class AxiLiteWriteRespChannel() extends Bundle {
  val resp = AxiResp()
}

class AxiLiteReadAddrChannel(addrWidth: Int) extends AxiLiteWriteAddrChannel(addrWidth)

class AxiLiteReadDataChannel(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val resp = AxiResp()
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

/** AMBA AXI-Lite IO bundle.
  *
  * @param dataWidth
  *   The bit width of the bus data. It can only be 64 or 32.
  * @param addrWidth
  *   The bit width of the bus address.
  */
class AxiLiteIO(dataWidth: Int, addrWidth: Int) extends Bundle with IsMasterSlave {
  override def isMaster = true

  // Do not check dataWidth, because many implementations violate this.
  // require(List(32, 64) contains dataWidth, s"Data width of AXI4-Lite bus can only be 32 or 64, but got $dataWidth")

  val aw = Master(stream.Stream(new AxiLiteWriteAddrChannel(addrWidth)))
  val w  = Master(stream.Stream(new AxiLiteWriteDataChannel(dataWidth)))
  val b  = Slave(stream.Stream(new AxiLiteWriteRespChannel()))
  val ar = Master(stream.Stream(new AxiLiteReadAddrChannel(addrWidth)))
  val r  = Slave(stream.Stream(new AxiLiteReadDataChannel(dataWidth)))

  val dataWidthByteNum: Int = dataWidth / 8
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
class Axi4IO(dataWidth: Int, addrWidth: Int, idWidth: Int) extends Bundle with IsMasterSlave {
  def isMaster = true

  // Do not check dataWidth, because many implementations violate this.
  // require(
  //   List(8, 16, 32, 64, 128, 256, 512, 1024) contains dataWidth,
  //   s"Data width of AXI4 bus can only be 8, 16, 32, 64, 128, 256, 512, or 1024, but got $dataWidth."
  // )

  val aw = Master(stream.Stream(new AxiWriteAddrChannel(addrWidth, idWidth)))
  val w  = Master(stream.Stream(new AxiWriteDataChannel(dataWidth))) // AXI4 does NOT have WID.
  val b  = Slave(stream.Stream(new AxiWriteRespChannel(idWidth)))
  val ar = Master(stream.Stream(new AxiReadAddrChannel(addrWidth, idWidth)))
  val r  = Slave(stream.Stream(new AxiReadDataChannel(dataWidth, idWidth)))

  val dataWidthByteNum: Int = dataWidth / 8
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
class Axi3IO(dataWidth: Int, addrWidth: Int, idWidth: Int) extends Axi4IO(dataWidth, addrWidth, idWidth) {
  override val aw = Master(stream.Stream(new AxiWriteAddrChannel(addrWidth, idWidth, lenWidth = 4, lockWidth = 2)))
  override val w  = Master(stream.Stream(new AxiWriteDataChannel(dataWidth, idWidth)))
  override val ar = Master(stream.Stream(new AxiReadAddrChannel(addrWidth, idWidth, lenWidth = 4, lockWidth = 2)))
}
