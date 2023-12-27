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

private[chipmunk] class AcornSimpleResponseChannel(val dataWidth: Int = 32, val statusWidth: Int = 1) extends Bundle {

  /** The responded read-back data. */
  val rdata = UInt(dataWidth.W)

  /** The responded status flags. */
  val status = UInt(statusWidth.W)
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
  * @param statusWidth
  *   Status width of the bus interface. Default is 1.
  * @param maskUnit
  *   The mask granularity for writing. In general, it should be a power of 2. Keep it 0 to generate no mask signal. For
  *   example, if `maskUnit` is 8, each mask bit corresponds to one byte of the data bits.
  */
class AcornSimpleIO(val addrWidth: Int = 32, val dataWidth: Int = 32, val statusWidth: Int = 1, val maskUnit: Int = 0)
    extends Bundle
    with IsMasterSlave {
  val hasMask: Boolean = maskUnit > 0
  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0

  val cmd  = Master(Stream(new AcornSimpleCommandChannel(addrWidth, dataWidth, maskWidth)))
  val resp = Slave(Stream(new AcornSimpleResponseChannel(dataWidth, statusWidth)))

  def isMaster = true
}

///** Acorn memory-mapped bus interface with a simple read/write-shared [[StreamIO]] channel.
//  *
//  * This class is similar to [[AcornSimpleIO]], but uses [[FlowIO]] instead of [[StreamIO]]. That is to say, the
//  * slave should always be ready to accept the request, and the master should always be ready to accept the response.
//  *
//  * @note
//  *   The mask bits (`wr.req.bits.mask`) are high-active. Only the data bits corresponding to mask bits of 1 will be
//  *   written to the register fields. That is to say, if all bits in `wr.req.bits.mask` is 0, no data will be written
//  *   during a write request.
//  *
//  * @param dataWidth
//  *   Data width of the bus interface. Default is 32.
//  * @param addrWidth
//  *   Address width of the bus interface. Default is 32.
//  * @param statusWidth
//  *   Status width of the bus interface. Default is 1.
//  * @param maskUnit
//  *   The mask granularity for writing. In general, it should be a power of 2. Keep it 0 to generate no mask signal. For
//  *   example, if `maskUnit` is 8, each mask bit corresponds to one byte of the data bits.
//  */
//class AcornSimpleFlowIO(
//  val addrWidth: Int = 32,
//  val dataWidth: Int = 32,
//  val statusWidth: Int = 1,
//  val maskUnit: Int = 0
//) extends Bundle
//    with IsMasterSlave {
//  val hasMask: Boolean = maskUnit > 0
//  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0
//
//  val cmd  = Master(Flow(new AcornSimpleCommandChannel(addrWidth, dataWidth, maskWidth)))
//  val resp = Slave(Flow(new AcornSimpleResponseChannel(dataWidth, statusWidth)))
//
//  def isMaster = true
//}

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

private[chipmunk] class AcornWideWriteResponseChannel(val statusWidth: Int = 1) extends Bundle {

  /** The responded status flags. */
  val status = UInt(statusWidth.W)
}

private[chipmunk] class AcornWideReadCommandChannel(val addrWidth: Int = 32) extends Bundle {

  /** The read address. */
  val addr = UInt(addrWidth.W)
}

private[chipmunk] class AcornWideReadResponseChannel(val dataWidth: Int = 32, val statusWidth: Int = 1) extends Bundle {

  /** The responded read-back data. */
  val rdata = UInt(dataWidth.W)

  /** The responded status flags. */
  val status = UInt(statusWidth.W)
}

/** Acorn memory-mapped bus interface with two independent [[StreamIO]] channels for read and write.
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
class AcornWideIO(val dataWidth: Int = 32, val addrWidth: Int = 32, val statusWidth: Int = 2, val maskUnit: Int = 0)
    extends Bundle
    with IsMasterSlave {
  val hasMask: Boolean = maskUnit > 0
  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0

  val wr = new Bundle {
    val cmd  = Master(Stream(new AcornWideWriteCommandChannel(addrWidth, dataWidth, maskWidth)))
    val resp = Slave(Stream(new AcornWideWriteResponseChannel(statusWidth)))
  }
  val rd = new Bundle {
    val cmd  = Master(Stream(new AcornWideReadCommandChannel(addrWidth)))
    val resp = Slave(Stream(new AcornWideReadResponseChannel(dataWidth, statusWidth)))
  }

  def isMaster = true
}

///** Acorn memory-mapped bus interface with two independent [[FlowIO]] channels for read and write.
//  *
//  * This class is similar to [[AcornWideIO]], but uses [[FlowIO]] instead of [[StreamIO]]. That is to say, the slave
//  * should always be ready to accept the request, and the master should always be ready to accept the response.
//  *
//  * @param dataWidth
//  *   Data width of the bus interface. Default is 32.
//  * @param addrWidth
//  *   Address width of the bus interface. Default is 32.
//  * @param statusWidth
//  *   Status width of the bus interface. Default is 1.
//  * @param maskUnit
//  *   Mask unit of the bus interface. Default is 0, which means no mask.
//  */
//class AcornWideFlowIO(
//  val dataWidth: Int = 32,
//  val addrWidth: Int = 32,
//  val statusWidth: Int = 2,
//  val maskUnit: Int = 0
//) extends Bundle
//    with IsMasterSlave {
//  val hasMask: Boolean = maskUnit > 0
//  val maskWidth: Int   = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0
//
//  val wr = new Bundle {
//    val cmd  = Master(Flow(new AcornWideWriteCommandChannel(addrWidth, dataWidth, maskWidth)))
//    val resp = Slave(Flow(new AcornWideWriteResponseChannel(statusWidth)))
//  }
//  val rd = new Bundle {
//    val cmd  = Master(Flow(new AcornWideReadCommandChannel(addrWidth)))
//    val resp = Slave(Flow(new AcornWideReadResponseChannel(dataWidth, statusWidth)))
//  }
//
//  def isMaster = true
//}