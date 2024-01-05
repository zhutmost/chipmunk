package chipmunk
package component.acorn

import stream._

import chisel3._

import scala.math.ceil

private[chipmunk] class AcornSimpleCommandChannel(
  val addrWidth: Int = 32,
  val dataWidth: Int = 32,
  val maskWidth: Int = 0
) extends Bundle {

  /** Whether the current request is a read request. */
  val read = Bool()

  /** The read/write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val wdata = UInt(dataWidth.W)

  /** The write mask. */
  val wmask = if (maskWidth != 0) Some(UInt(maskWidth.W)) else None
}

private[chipmunk] class AcornSimpleResponseChannel(val dataWidth: Int = 32) extends Bundle {

  /** The responded read-back data. */
  val rdata = UInt(dataWidth.W)

  /** The responded status flags. */
  val status = Bool()
}

/** Acorn memory-mapped bus interface with a simple read/write-shared channel.
  *
  * @note
  *   The mask bits (`wr.req.bits.wmask`) are high-active. Only the data bits corresponding to mask bits of 1 will be
  *   written to the register fields. That is to say, if all bits in `wr.req.bits.mask` is 0, no data will be written
  *   during a write request.
  *
  * @param dataWidth
  *   Data width of the bus interface. Default is 32.
  * @param addrWidth
  *   Address width of the bus interface. Default is 32.
  * @param maskUnit
  *   The mask granularity for writing. In general, it should be a power of 2. Keep it 0 to generate no mask signal. For
  *   example, if `maskUnit` is 8, each mask bit corresponds to one byte of the data bits.
  */
class AcornSimpleIO(val addrWidth: Int = 32, val dataWidth: Int = 32, val maskUnit: Int = 0)
    extends Bundle
    with IsMasterSlave {
  require(dataWidth > 0, s"Data width of Acorn bus must be at least 1, but got $dataWidth")
  require(addrWidth > 0, s"Address width of Acorn bus must be at least 1, but got $addrWidth")

  val hasMask: Boolean = maskUnit > 0
  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0

  val cmd  = Master(Stream(new AcornSimpleCommandChannel(addrWidth, dataWidth, maskWidth)))
  val resp = Slave(Stream(new AcornSimpleResponseChannel(dataWidth)))

  def isMaster = true
}

private[chipmunk] class AcornWideWriteCommandChannel(
  val addrWidth: Int = 32,
  val dataWidth: Int = 32,
  val maskWidth: Int = 0
) extends Bundle {

  /** The write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val wdata = UInt(dataWidth.W)

  /** The write mask. */
  val wmask = if (maskWidth != 0) Some(UInt(maskWidth.W)) else None
}

private[chipmunk] class AcornWideWriteResponseChannel() extends Bundle {

  /** The responded status flags. */
  val status = Bool()
}

private[chipmunk] class AcornWideReadCommandChannel(val addrWidth: Int = 32) extends Bundle {

  /** The read address. */
  val addr = UInt(addrWidth.W)
}

private[chipmunk] class AcornWideReadResponseChannel(val dataWidth: Int = 32) extends Bundle {

  /** The responded read-back data. */
  val rdata = UInt(dataWidth.W)

  /** The responded status flags. */
  val status = Bool()
}

/** Acorn memory-mapped bus interface with two independent [[StreamIO]] channels for read and write.
  *
  * @param dataWidth
  *   Data width of the bus interface. Default is 32.
  * @param addrWidth
  *   Address width of the bus interface. Default is 32.
  * @param maskUnit
  *   Mask unit of the bus interface. Default is 0, which means no mask.
  */
class AcornWideIO(val dataWidth: Int = 32, val addrWidth: Int = 32, val maskUnit: Int = 0)
    extends Bundle
    with IsMasterSlave {
  require(dataWidth > 0, s"Data width of Acorn bus must be at least 1, but got $dataWidth")
  require(addrWidth > 0, s"Address width of Acorn bus must be at least 1, but got $addrWidth")

  val hasMask: Boolean = maskUnit > 0
  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0

  val wr = new Bundle {
    val cmd  = Master(Stream(new AcornWideWriteCommandChannel(addrWidth, dataWidth, maskWidth)))
    val resp = Slave(Stream(new AcornWideWriteResponseChannel()))
  }
  val rd = new Bundle {
    val cmd  = Master(Stream(new AcornWideReadCommandChannel(addrWidth)))
    val resp = Slave(Stream(new AcornWideReadResponseChannel(dataWidth)))
  }

  def isMaster = true
}
