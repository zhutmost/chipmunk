package chipmunk

import chisel3._

/** Convert a [[UInt]] from binary code to gray code. */
object UIntToGray {
  def apply(in: UInt): UInt = in ^ (in >> 1).asUInt
}

/** Convert a [[UInt]] from gray code to binary code. */
object GrayToUInt {
  def apply(in: UInt): UInt = {
    val out = Vec(in.getWidth, Bool())
    for (i <- 0 until in.getWidth) {
      out(i) := in(in.getWidth - 1, i).xorR
    }
    out.asUInt
  }
}
