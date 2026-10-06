package chipmunk

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.PriorityMux

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

/** Encode the lowest-index asserted input, returning default when none are asserted.
  *
  * For Bits, bit zero has highest priority. An empty sequence returns default.
  */
object PriorityEncoderDefault {

  def apply(in: Seq[Bool], default: UInt)(using SourceInfo): UInt =
    MuxPriority(in.zipWithIndex.map { case (active, index) => active -> index.U }, default)

  def apply(in: Bits, default: UInt)(using SourceInfo): UInt =
    apply(in.asBools, default)
}
