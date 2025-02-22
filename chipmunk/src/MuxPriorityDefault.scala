package chipmunk

import chisel3._

/** Builds a Mux tree under the assumption that multiple select signals can be enabled. Priority is given to the first
  * select signal. If no select signal is enabled, the default value is returned.
  *
  * @see
  *   [[chisel3.util.PriorityMux]], [[MuxPriority]]
  */
object MuxPriorityDefault {
  def apply[T <: Data](in: Seq[(Bool, T)], default: T): T = {
    in.size match {
      case 1 =>
        Mux(in.head._1, in.head._2, default)
      case _ =>
        Mux(in.head._1, in.head._2, apply(in.tail, default))
    }
  }

  def apply[T <: Data](sel: Seq[Bool], in: Seq[T], default: T): T = apply(sel.zip(in), default)

  def apply[T <: Data](sel: Bits, in: Seq[T], default: T): T = apply(in.indices.map(sel(_)), in, default)
}

/** Returns the bit position of the least-significant high bit of the input bitvector. If no bits are high, the default
  * value is returned.
  *
  * @see
  *   [[chisel3.util.PriorityEncoder]]
  */
object PriorityEncoderDefault {
  def apply(in: Seq[Bool], default: UInt): UInt = MuxPriorityDefault(in.zipWithIndex.map(x => x._1 -> x._2.U), default)

  def apply(in: Bits, default: UInt): UInt = apply(in.asBools, default)
}
