package chipmunk

import chisel3._

/** Can be connected as master/slave interface.
  *
  * [[Bundle]] mixes this trait so that it can use [[Master]] and [[Slave]] to indicate the direction of transmission.
  * The function [[isMaster]] needs to be set true or false to indicate whether the bundle is Master or Slave.
  *
  * @example
  *   {{{
  * class SomeIO extends Bundle with IsMasterSlave {
  *   val otherPorts = Input(UInt(2.W))
  *   override def isMaster: Boolean = true
  * }
  *   }}}
  */
trait IsMasterSlave {
  this: Bundle {} =>

  /** Override this method to set the bundle as Master or Slave. */
  def isMaster: Boolean

  // A flag indicating whether this Bundle has been wrapped with Master/Slave(_).
  private[chipmunk] var _wrapFlag: Option[Boolean] = None
}

/** Set a bundle as a master interface. */
object Master {
  def apply[T <: Bundle with IsMasterSlave](bundle: => T): T = {
    require(bundle._wrapFlag.isEmpty, "Bundles cannot be nested-ly wrapped with Master/Slave(_).")
    val b: T = if (bundle.isMaster) bundle else Flipped(bundle)
    b._wrapFlag = Some(true)
    b
  }
}

/** Set a bundle as a slave interface. */
object Slave {
  def apply[T <: Bundle with IsMasterSlave](bundle: => T): T = {
    require(bundle._wrapFlag.isEmpty, "Bundles cannot be nested-ly wrapped with Master/Slave(_).")
    val b: T = if (!bundle.isMaster) bundle else Flipped(bundle)
    b._wrapFlag = Some(false)
    b
  }
}
