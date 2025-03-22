import chisel3._
import chisel3.util._

package chipmunk {

  /** A Bundle with no elements. */
  final class EmptyBundle extends Bundle
}

package object chipmunk {

  implicit class AddMethodsToBits[T <: Bits](c: T) {

    /** Returns the most significant n bits. */
    def msBits(n: Int = 1): UInt = c.head(n)

    /** Returns the least significant n bits. */
    def lsBits(n: Int = 1): UInt = c(n - 1, 0)

    /** Returns the most significant bit as Bool. */
    def msBit: Bool = c.head(1).asBool

    /** Returns the least significant bit as Bool. */
    def lsBit: Bool = c(0).asBool

    /** Returns a signal having the same type as this signal, but with all bits set to the specified Bool literal. */
    def filledWith(b: Bool): T = Fill(c.getWidth, b).asTypeOf(c)

    /** Returns a signal having the same type as this signal, but with all bits set to the specified Boolean literal. */
    def filledWith(b: Boolean): T = filledWith(b.B)

    /** Returns a signal having the same type as this signal, but with all bits set to True. */
    def filledOnes: T = filledWith(true)

    /** Returns a signal having the same type as this signal, but with all bits set to False. */
    def filledZeros: T = filledWith(false)

    /** Assign all bits to True. */
    def assignOnes(): Unit = c := c.filledOnes

    /** Assign all bits to False. */
    def assignZeros(): Unit = c := c.filledZeros

    /** Returns whether the number is one-hot encoded. */
    def isOneHot: Bool = {
      val parity = Wire(Vec(c.getWidth, Bool()))
      parity(0) := c(0)
      for (i <- 1 until c.getWidth) {
        parity(i) := parity(i - 1) ^ c(i)
      }
      val ret = parity(c.getWidth - 1) && (parity.asUInt | (~c).asUInt).andR
      ret
    }
  }

  implicit class AddMethodsToData[T <: Data](c: T) {

    /** Mark this signal as an optimization barrier to Chisel and FIRRTL. Same as `chisel3.dontTouch(x)`. */
    def dontTouch: T = {
      chisel3.dontTouch(c)
    }
  }

  /** Alias for [[chisel3.util.PriorityMux]].
    *
    * @note
    *   Chisel has many muxes named `MuxXxx`, but this is the only one that is named as `XxxMux`.
    */
  val MuxPriority = PriorityMux
}
