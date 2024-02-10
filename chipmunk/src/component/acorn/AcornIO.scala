package chipmunk
package component.acorn

import stream._

import chisel3._

import scala.math.ceil

private[acorn] object AcornUtils {
  def maskWidth(dataWidth: Int, maskUnit: Int = 8): Int = {
    val maskWidth: Int = math.ceil(dataWidth.toDouble / maskUnit).toInt
    maskWidth
  }
}

private[chipmunk] class AcornSpCommandChannel(val dataWidth: Int = 32, val addrWidth: Int = 32) extends Bundle {
  val maskWidth: Int = AcornUtils.maskWidth(dataWidth)

  /** Whether the current request is a read request. */
  val read = Bool()

  /** The read/write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val wdata = UInt(dataWidth.W)

  /** The write mask. */
  val wmask = UInt(maskWidth.W)
}

private[chipmunk] class AcornSpResponseChannel(val dataWidth: Int = 32) extends Bundle {

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
  */
class AcornSpIO(val dataWidth: Int = 32, val addrWidth: Int = 32) extends Bundle with IsMasterSlave {
  require(dataWidth > 0, s"Data width of Acorn bus must be at least 1, but got $dataWidth")
  require(addrWidth > 0, s"Address width of Acorn bus must be at least 1, but got $addrWidth")

  val cmd  = Master(Stream(new AcornSpCommandChannel(dataWidth, addrWidth)))
  val resp = Slave(Stream(new AcornSpResponseChannel(dataWidth)))

  val maskWidth: Int = cmd.bits.maskWidth

  def isMaster = true
}

private[chipmunk] class AcornDpWriteCommandChannel(val dataWidth: Int = 32, val addrWidth: Int = 32) extends Bundle {
  val maskWidth: Int = AcornUtils.maskWidth(dataWidth)

  /** The write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val wdata = UInt(dataWidth.W)

  /** The write mask. */
  val wmask = UInt(maskWidth.W)
}

private[chipmunk] class AcornDpWriteResponseChannel() extends Bundle {

  /** The responded status flags. */
  val status = Bool()
}

private[chipmunk] class AcornDpReadCommandChannel(val addrWidth: Int = 32) extends Bundle {

  /** The read address. */
  val addr = UInt(addrWidth.W)
}

private[chipmunk] class AcornDpReadResponseChannel(val dataWidth: Int = 32) extends Bundle {

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
  */
class AcornDpIO(val dataWidth: Int = 32, val addrWidth: Int = 32) extends Bundle with IsMasterSlave {
  require(dataWidth > 0, s"Data width of Acorn bus must be at least 1, but got $dataWidth")
  require(addrWidth > 0, s"Address width of Acorn bus must be at least 1, but got $addrWidth")

  val wr = new Bundle {
    val cmd  = Master(Stream(new AcornDpWriteCommandChannel(dataWidth, addrWidth)))
    val resp = Slave(Stream(new AcornDpWriteResponseChannel()))
  }
  val rd = new Bundle {
    val cmd  = Master(Stream(new AcornDpReadCommandChannel(addrWidth)))
    val resp = Slave(Stream(new AcornDpReadResponseChannel(dataWidth)))
  }

  val maskWidth: Int = wr.cmd.bits.maskWidth

  def isMaster = true
}
