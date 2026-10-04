package chipmunk
package acorn

/** A byte-addressed, naturally aligned, fixed-word interface. */
final case class AcornParams(dataWidth: Int, addrWidth: Int) {
  require(dataWidth >= 8 && (dataWidth & (dataWidth - 1)) == 0, "Acorn data width must be a power of two >= 8.")
  require(addrWidth >= 1, "Acorn address width must be positive.")
  val bytesPerWord: Int = dataWidth / 8
}

/** Separate capacities for accepted commands whose responses have not yet transferred. */
final case class AcornOutstanding(read: Int = 4, write: Int = 4) {
  require(read >= 1 && write >= 1, "Acorn outstanding capacities must be positive.")
}

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
      slave.base        % params.bytesPerWord == 0 && slave.size % params.bytesPerWord == 0 &&
        slave.localBase % params.bytesPerWord == 0,
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
