package chipmunk
package component.acorn

import stream._

import chisel3._

class AcornWrCmdChannel(val dataWidth: Int, val addrWidth: Int) extends Bundle {

  /** The write address. */
  val addr = UInt(addrWidth.W)

  /** The write data. */
  val data = UInt(dataWidth.W)

  /** The byte mask. */
  val strobe = UInt(AcornIO.strobeWidth(dataWidth).W)
}

class AcornWrRspChannel() extends Bundle {

  /** Whether the write access fails. */
  val error = Bool()
}

class AcornRdCmdChannel(val addrWidth: Int) extends Bundle {

  /** The read address. */
  val addr = UInt(addrWidth.W)
}

class AcornRdRspChannel(val dataWidth: Int) extends Bundle {

  /** The responded read-back data. */
  val data = UInt(dataWidth.W)

  /** Whether the read access fails. */
  val error = Bool()
}

class AcornIO(val dataWidth: Int, val addrWidth: Int) extends Bundle with IsMasterSlave {
  override def isMaster = true

  require(
    dataWidth > 0 && (dataWidth & (dataWidth - 1)) == 0,
    s"Data width of Acorn bus must be power of 2, but got $dataWidth."
  )
  require(addrWidth > 0, s"Address width of Acorn bus must be at least 1, but got $addrWidth.")

  val strobeWidth: Int = AcornIO.strobeWidth(dataWidth)

  val rd = new Bundle {
    val cmd = Master(Stream(new AcornRdCmdChannel(addrWidth)))
    val rsp = Slave(Stream(new AcornRdRspChannel(dataWidth)))
  }
  val wr = new Bundle {
    val cmd = Master(Stream(new AcornWrCmdChannel(dataWidth, addrWidth)))
    val rsp = Slave(Stream(new AcornWrRspChannel()))
  }
}

object AcornIO {
  def strobeWidth(dataWidth: Int, maskUnit: Int = 8): Int = {
    val strobeWidth: Int = math.ceil(dataWidth.toDouble / maskUnit).toInt
    strobeWidth
  }
}
