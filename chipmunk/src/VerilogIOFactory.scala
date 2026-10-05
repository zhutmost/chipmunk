package chipmunk

import scala.util.NotGiven

import chisel3.*
import chisel3.reflect.DataMirror

/** Selects a flat record while preserving optional interface capabilities. */
trait VerilogIOFactory[T <: Record & HasVerilogIO] {
  type Out <: VerilogIO[T]

  def create(gen: T, rename: String => String): Out
}

object VerilogIOFactory {
  type Aux[T <: Record & HasVerilogIO, R <: VerilogIO[T]] = VerilogIOFactory[T] { type Out = R }

  given plain[T <: Record & HasVerilogIO](using NotGiven[T <:< IsMasterSlave]): Aux[T, VerilogIO[T]] =
    new VerilogIOFactory[T] {
      type Out = VerilogIO[T]

      override def create(gen: T, rename: String => String): Out = new VerilogIO(gen, rename)
    }

  given masterSlave[T <: Record & IsMasterSlave & HasVerilogIO]: Aux[T, MasterSlaveVerilogIO[T]] =
    new VerilogIOFactory[T] {
      type Out = MasterSlaveVerilogIO[T]

      override def create(gen: T, rename: String => String): Out = {
        val result = new MasterSlaveVerilogIO(gen, rename)
        result._roleWrapped = gen._roleWrapped
        result
      }
    }
}

/** Adds Master/Slave support only when the original interface provides it. */
final class MasterSlaveVerilogIO[T <: Record & IsMasterSlave & HasVerilogIO] private[chipmunk] (
  private val gen: T,
  private val rename: String => String,
) extends VerilogIO[T](gen, rename)
    with IsMasterSlave {
  // The root direction of gen has already been absorbed into the flat fields.
  override val isMaster: Boolean = DataMirror.specifiedDirectionOf(gen) match {
    case SpecifiedDirection.Unspecified => gen.isMaster
    case SpecifiedDirection.Flip        => !gen.isMaster
    case other                          =>
      throw new IllegalArgumentException(s"MasterSlaveVerilogIO requires an uncoerced interface, found $other.")
  }
}
