package chipmunk
package component.acorn

import stream._

import chisel3._

import scala.math.ceil

private[acorn] abstract class AcornIOBase(val dataWidth: Int, val addrWidth: Int) extends Bundle with IsMasterSlave {
  override def isMaster = true

  require(dataWidth > 0, s"Data width of Acorn bus must be at least 1, but got $dataWidth.")
  require(addrWidth > 0, s"Address width of Acorn bus must be at least 1, but got $addrWidth.")

  val maskWidth: Int = AcornIOBase.maskWidth(dataWidth)
}

private[acorn] object AcornIOBase {
  def maskWidth(dataWidth: Int, maskUnit: Int = 8): Int = {
    val maskWidth: Int = math.ceil(dataWidth.toDouble / maskUnit).toInt
    maskWidth
  }
}

private[acorn] class AcornSpCommandChannel(val dataWidth: Int, val addrWidth: Int) extends Bundle {
  val maskWidth: Int = AcornIOBase.maskWidth(dataWidth)

  /** Whether the current request is a read request. */
  val read = Bool()

  /** The read/write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val wdata = UInt(dataWidth.W)

  /** The write mask. */
  val wmask = UInt(maskWidth.W)
}

private[acorn] class AcornSpResponseChannel(val dataWidth: Int) extends Bundle {

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
class AcornSpIO(dataWidth: Int, addrWidth: Int) extends AcornIOBase(dataWidth, addrWidth) {
  val cmd  = Master(Stream(new AcornSpCommandChannel(dataWidth, addrWidth)))
  val resp = Slave(Stream(new AcornSpResponseChannel(dataWidth)))
}

private[acorn] class AcornDpWriteCommandChannel(val dataWidth: Int, val addrWidth: Int) extends Bundle {
  val maskWidth: Int = AcornIOBase.maskWidth(dataWidth)

  /** The write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val wdata = UInt(dataWidth.W)

  /** The write mask. */
  val wmask = UInt(maskWidth.W)
}

private[acorn] class AcornDpWriteResponseChannel() extends Bundle {

  /** The responded status flags. */
  val status = Bool()
}

private[acorn] class AcornDpReadCommandChannel(val addrWidth: Int) extends Bundle {

  /** The read address. */
  val addr = UInt(addrWidth.W)
}

private[acorn] class AcornDpReadResponseChannel(val dataWidth: Int) extends Bundle {

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
class AcornDpIO(dataWidth: Int, addrWidth: Int) extends AcornIOBase(dataWidth, addrWidth) {
  val wr = new Bundle {
    val cmd  = Master(Stream(new AcornDpWriteCommandChannel(dataWidth, addrWidth)))
    val resp = Slave(Stream(new AcornDpWriteResponseChannel()))
  }
  val rd = new Bundle {
    val cmd  = Master(Stream(new AcornDpReadCommandChannel(addrWidth)))
    val resp = Slave(Stream(new AcornDpReadResponseChannel(dataWidth)))
  }
}
