package chipmunk
package acorn

import chisel3.*

import chipmunk.stream.*

/** A byte-addressed, naturally aligned, fixed-word interface. */
final case class AcornParams(dataWidth: Int, addrWidth: Int) {
  require(dataWidth >= 8 && (dataWidth & (dataWidth - 1)) == 0, "Acorn data width must be a power of two >= 8.")
  require(addrWidth >= 1, "Acorn address width must be positive.")
  val strobeWidth: Int = dataWidth / 8
}

/** Separate capacities for accepted commands whose responses have not yet transferred. */
final case class AcornOutstanding(read: Int = 4, write: Int = 4) {
  require(read >= 1 && write >= 1, "Acorn outstanding capacities must be positive.")
}

class AcornWrCmdChannel(val dataWidth: Int, val addrWidth: Int) extends Bundle {
  val addr   = UInt(addrWidth.W)
  val data   = UInt(dataWidth.W)
  val strobe = UInt(AcornIO.strobeWidth(dataWidth).W)
}

class AcornWrRspChannel extends Bundle {
  val error = Bool()
}

class AcornRdCmdChannel(val addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
}

class AcornRdRspChannel(val dataWidth: Int) extends Bundle {
  val data  = UInt(dataWidth.W)
  val error = Bool()
}

/** Four independent Stream channels, declared from the master's perspective.
  *
  * Every accepted command has exactly one response. Each direction returns in command order; read and write have no
  * implicit ordering relative to one another. All producers hold valid/payload until transfer. Strobe bit i enables
  * data bits [8*i+7:8*i]. A write response means that the endpoint-defined write operation has completed.
  *
  * These routers register transaction metadata. An endpoint must accept a command without waiting for that command's
  * response to transfer, and must retain any response until accepted. All connected components share clock and reset;
  * reset cancels outstanding transactions and must reset the endpoints consistently.
  */
class AcornIO(val dataWidth: Int, val addrWidth: Int) extends Bundle with IsMasterSlave {
  def this(params: AcornParams) = this(params.dataWidth, params.addrWidth)

  val params: AcornParams        = AcornParams(dataWidth, addrWidth)
  val strobeWidth: Int           = params.strobeWidth
  override def isMaster: Boolean = true

  val rd = new Bundle {
    val cmd = Master(Stream(new AcornRdCmdChannel(addrWidth)))
    val rsp = Slave(Stream(new AcornRdRspChannel(dataWidth)))
  }
  val wr = new Bundle {
    val cmd = Master(Stream(new AcornWrCmdChannel(dataWidth, addrWidth)))
    val rsp = Slave(Stream(new AcornWrRspChannel))
  }
}

object AcornIO {
  def strobeWidth(dataWidth: Int): Int = {
    require(dataWidth >= 8 && dataWidth % 8 == 0, "Acorn strobes select whole bytes.")
    dataWidth / 8
  }
}
