package chipmunk

import chisel3.*

/** Marks a Record whose fields have a declared master or slave orientation.
  *
  * `isMaster` describes how the fields are declared in this type. It does not inspect the effective direction of a
  * bound Chisel port.
  *
  * Use [[Master]] or [[Slave]] when placing this type in another interface. Subfields may themselves be wrapped with
  * Master or Slave.
  *
  * Example:
  * {{{
  * class SramIO extends Bundle with IsMasterSlave {
  *   override def isMaster: Boolean = false
  *   val addr = Input(UInt(16.W))
  *   val data = Output(UInt(32.W))
  * }
  * }}}
  */
trait IsMasterSlave {
  this: Record =>

  /** Whether this type's fields are declared from the master's perspective. */
  def isMaster: Boolean

  // Guards against applying Master/Slave twice to the same Record instance.
  // This is library bookkeeping, not Chisel direction information.
  private[chipmunk] var _roleWrapped: Boolean = false
}

private[chipmunk] object DirectedRecord {

  def apply[T <: Record & IsMasterSlave](record: => T, asMaster: Boolean): T = {
    // A by-name argument must be evaluated exactly once.
    val original = record

    require(!original._roleWrapped, "The same Record cannot be wrapped twice with Master/Slave.")

    val directed: T =
      if (original.isMaster == asMaster) original
      else Flipped(original)

    // Flipped may return a clone. Mark both the supplied instance and
    // the returned instance so neither can be wrapped again directly.
    original._roleWrapped = true
    directed._roleWrapped = true
    directed
  }
}

/** Presents a [[Record]] from the master's perspective. */
object Master {
  def apply[T <: Record & IsMasterSlave](record: => T): T =
    DirectedRecord(record, asMaster = true)
}

/** Presents a [[Record]] from the slave's perspective. */
object Slave {
  def apply[T <: Record & IsMasterSlave](record: => T): T =
    DirectedRecord(record, asMaster = false)
}
