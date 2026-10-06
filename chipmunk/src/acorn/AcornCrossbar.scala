package chipmunk
package acorn

import chisel3.*
import chisel3.util.MixedVec

enum AcornArbitration {
  case RoundRobin, LowerFirst
}

/** One slave's global byte-address window [base, base + size).
  *
  * The endpoint sees localBase + (address - base). Its address width is inferred from the local window unless
  * localAddrWidth is specified. All ports have the common bus data width; width conversion is a separate adapter.
  */
final case class AcornSlaveConfig(
  name: String,
  base: BigInt,
  size: BigInt,
  localBase: BigInt = 0,
  localAddrWidth: Option[Int] = None,
  readable: Boolean = true,
  writable: Boolean = true,
) {
  require(name.trim.nonEmpty, "An Acorn slave needs a name.")
  require(base >= 0 && localBase >= 0 && size > 0, "Address bases must be nonnegative and size must be positive.")
  require(readable || writable, "An Acorn slave must support at least one direction.")

  val end: BigInt        = base + size
  val portAddrWidth: Int = localAddrWidth.getOrElse(math.max(1, (localBase + size - 1).bitLength))
  require(portAddrWidth >= 1, "A slave address width must be positive.")
  require(localBase + size <= (BigInt(1) << portAddrWidth), s"Local address window overflows slave $name.")
}

/** Static crossbar configuration. Slave sequence order is the order of io.outs.
  *
  * connections=None connects every master to every slave. Otherwise there must be one set of slave names per master; an
  * empty set disables all accesses from that master. Disallowed accesses receive normal ordered error responses.
  * masterOutstanding limits each master's in-flight commands; slaveOutstanding limits each slave's aggregate load.
  */
final case class AcornCrossbarConfig(
  params: AcornParams,
  numMasters: Int,
  slaves: Seq[AcornSlaveConfig],
  masterOutstanding: AcornOutstanding = AcornOutstanding(),
  slaveOutstanding: AcornOutstanding = AcornOutstanding(),
  arbitration: AcornArbitration = AcornArbitration.RoundRobin,
  connections: Option[Seq[Set[String]]] = None,
) {
  require(numMasters >= 1 && slaves.nonEmpty, "An Acorn crossbar needs at least one master and one slave.")
  private val names = slaves.map(_.name).toSet
  require(names.size == slaves.size, "Acorn slave names must be unique.")
  for (slave <- slaves) {
    require(slave.end <= (BigInt(1) << params.addrWidth), s"Global address window overflows slave ${slave.name}.")
    require(
      slave.base        % params.strobeWidth == 0 && slave.size % params.strobeWidth == 0 &&
        slave.localBase % params.strobeWidth == 0,
      s"Address window for ${slave.name} must be word aligned.",
    )
  }
  require(
    slaves.sortBy(_.base).sliding(2).forall(pair => pair.size < 2 || pair.head.end <= pair.last.base),
    "Acorn slave address windows must not overlap.",
  )
  connections.foreach { sets =>
    require(sets.size == numMasters, "connections must contain one set per master.")
    require(sets.forall(_.subsetOf(names)), "connections contains an unknown slave name.")
  }

  def canAccess(master: Int, slave: Int): Boolean =
    connections.forall(_(master).contains(slaves(slave).name))
}

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
    val aligned = (addr & (params.strobeWidth - 1).U) === 0.U
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
