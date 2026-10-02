package chipmunk

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.PriorityMux

/** A Bundle with no payload fields. Useful as a typed empty payload. */
final class EmptyBundle extends Bundle

/** Alias for [[chisel3.util.PriorityMux]] with additional overloads accepting an explicit default.
  *
  * When a default is provided, it is selected if no select signal is asserted.
  *
  * @note
  *   Uses the `MuxXxx` naming convention for consistency with other Chisel mux utilities.
  */
object MuxPriority {

  def apply[T <: Data](in: Seq[(Bool, T)])(using SourceInfo): T = PriorityMux(in)

  def apply[T <: Data](sel: Seq[Bool], in: Seq[T])(using SourceInfo): T = PriorityMux(sel, in)

  def apply[T <: Data](sel: Bits, in: Seq[T])(using SourceInfo): T = PriorityMux(sel, in)

  def apply[T <: Data](in: Seq[(Bool, T)], default: T)(using SourceInfo): T =
    PriorityMux(in :+ (true.B -> default))

  def apply[T <: Data](sel: Seq[Bool], in: Seq[T], default: T)(using SourceInfo): T =
    apply(sel.zip(in), default)

  def apply[T <: Data](sel: Bits, in: Seq[T], default: T)(using SourceInfo): T =
    apply(sel.asBools.zip(in), default)
}
