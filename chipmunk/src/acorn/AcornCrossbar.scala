package chipmunk
package acorn

import chisel3.*
import chisel3.util.MixedVec

/** Per-master Demux followed by per-slave Mux. Different slaves arbitrate independently.
  *
  * Address misses, unaligned addresses, direction permissions, and disconnected routes use one private error endpoint
  * per master. Errors participate in the same response ordering as successful accesses. There are no payload queues
  * between Demux and Mux: an accepted external command transfers to its endpoint in that same cycle, and both route
  * records are updated atomically. A slow or stalled response can cause head-of-line blocking on shared slaves.
  */
class AcornCrossbar(val config: AcornCrossbarConfig) extends Module {
  private val params    = config.params
  private val numSlaves = config.slaves.size
  val io                = IO(new Bundle {
    val ins  = Vec(config.numMasters, Slave(new AcornIO(params)))
    val outs = MixedVec(config.slaves.map { slave =>
      Master(new AcornIO(params.dataWidth, slave.portAddrWidth))
    })
  })

  /** Select an output by its configured name. */
  def slave(name: String): AcornIO = {
    val index = config.slaves.indexWhere(_.name == name)
    require(index >= 0, s"Unknown Acorn slave: $name")
    io.outs(index)
  }

  private def decode(addr: UInt, master: Int, write: Boolean): UInt = {
    val aligned = (addr & (params.bytesPerWord - 1).U) === 0.U
    val hits    = config.slaves.zipWithIndex.map { case (slave, index) =>
      val enabled = config.canAccess(master, index) && (if (write) slave.writable else slave.readable)
      val hit     = enabled.B && aligned && addr >= slave.base.U && addr <= (slave.end - 1).U
      hit -> index.U
    }
    chisel3.util.MuxCase(numSlaves.U, hits)
  }

  private val demuxes = Seq.tabulate(config.numMasters) { master =>
    val demux = Module(new AcornDemux(params, numSlaves + 1, config.masterOutstanding))
    demux.io.in <> io.ins(master)
    demux.io.rdSelect := decode(io.ins(master).rd.cmd.bits.addr, master, write = false)
    demux.io.wrSelect := decode(io.ins(master).wr.cmd.bits.addr, master, write = true)
    val errors = Module(new AcornErrorPoint(params, config.masterOutstanding))
    errors.io.access <> demux.io.outs(numSlaves)
    demux
  }

  for ((region, target) <- config.slaves.zipWithIndex) {
    val localParams = AcornParams(params.dataWidth, region.portAddrWidth)
    val mux         = Module(new AcornMux(localParams, config.numMasters, config.slaveOutstanding, config.arbitration))
    io.outs(target) <> mux.io.out
    for (master <- 0 until config.numMasters) {
      val source = demuxes(master).io.outs(target)
      mux.io.ins(master).rd.cmd << source.rd.cmd.payloadMap { command =>
        val local = Wire(new AcornRdCmdChannel(region.portAddrWidth))
        local.addr := (command.addr - region.base.U) +& region.localBase.U
        local
      }
      mux.io.ins(master).wr.cmd << source.wr.cmd.payloadMap { command =>
        val local = Wire(new AcornWrCmdChannel(params.dataWidth, region.portAddrWidth))
        local.addr   := (command.addr - region.base.U) +& region.localBase.U
        local.data   := command.data
        local.strobe := command.strobe
        local
      }
      source.rd.rsp << mux.io.ins(master).rd.rsp
      source.wr.rsp << mux.io.ins(master).wr.rsp
    }
  }
}
