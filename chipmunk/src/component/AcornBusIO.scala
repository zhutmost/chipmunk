package chipmunk
package component

import stream._

import chisel3._

import scala.math.ceil

/** Acorn memory-mapped bus interface with a simple read/write-shared channel.
  *
  * @note
  *   The mask bits (`wr.req.bits.mask`) are high-active. Only the data bits corresponding to mask bits of 1 will be
  *   written to the register fields. That is to say, if all bits in `wr.req.bits.mask` is 0, no data will be written
  *   during a write request.
  *
  * @param dataWidth
  *   Data width of the bus interface. Default is 32.
  * @param addrWidth
  *   Address width of the bus interface. Default is 32.
  * @param statusWidth
  *   Status width of the bus interface. Default is 1.
  * @param maskUnit
  *   The mask granularity for writing. In general, it should be a power of 2. Keep it 0 to generate no mask signal. For
  *   example, if `maskUnit` is 8, each mask bit corresponds to one byte of the data bits.
  */
class AcornSimpleBusIO(
  val addrWidth: Int = 32,
  val dataWidth: Int = 32,
  val statusWidth: Int = 1,
  val maskUnit: Int = 0
) extends Bundle
    with IsMasterSlave {
  val hasMask: Boolean = maskUnit > 0
  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0

  val cmd = Master(Stream(new Bundle {

    /** Whether the current request is a read request. */
    val read = Bool()

    /** The read/write address. */
    val addr = UInt(addrWidth.W)

    /** The write data. */
    val wdata = UInt(dataWidth.W)

    /** The write mask. */
    val wmask = if (hasMask) Some(UInt(maskWidth.W)) else None
  }))
  val resp = Slave(Stream(new Bundle {

    /** The responded read-back data. */
    val rdata = UInt(dataWidth.W)

    /** The responded status flags. */
    val status = UInt(statusWidth.W)
  }))

  def isMaster = true
}

/** Acorn memory-mapped bus interface with two independent channels for read and write.
  *
  * @param dataWidth
  *   Data width of the bus interface. Default is 32.
  * @param addrWidth
  *   Address width of the bus interface. Default is 32.
  * @param statusWidth
  *   Status width of the bus interface. Default is 1.
  * @param maskUnit
  *   Mask unit of the bus interface. Default is 0, which means no mask.
  */
class AcornWideBusIO(val dataWidth: Int = 32, val addrWidth: Int = 32, val statusWidth: Int = 2, val maskUnit: Int = 0)
    extends Bundle
    with IsMasterSlave {
  val hasMask: Boolean = maskUnit > 0
  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0

  val wr = new Bundle {
    val cmd = Master(Stream(new Bundle {

      /** The write address. */
      val addr = UInt(addrWidth.W)

      /** The write data. */
      val wdata = UInt(dataWidth.W)

      /** write mask. */
      val wmask = if (hasMask) Some(UInt(maskWidth.W)) else None
    }))
    val resp = Slave(Stream(new Bundle {

      /** The responded status flags. */
      val status = UInt(statusWidth.W)
    }))
  }

  val rd = new Bundle {
    val cmd = Master(Stream(new Bundle {

      /** The read address. */
      val addr = UInt(addrWidth.W)
    }))
    val resp = Slave(Stream(new Bundle {

      /** The responded read-back data. */
      val rdata = UInt(dataWidth.W)

      /** The responded status flags. */
      val status = UInt(statusWidth.W)
    }))
  }

  def isMaster = true
}
