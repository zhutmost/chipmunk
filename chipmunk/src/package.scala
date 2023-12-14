import chisel3._
import chisel3.util._

package object chipmunk {

  /** A Bundle with no elements. */
  final class EmptyBundle extends Bundle

  implicit class AddMethodsToBits[T <: Bits](c: T) {

    /** returns the most significant n bits. */
    def msBits(n: Int = 1): UInt = c.head(n)

    /** returns the least significant n bits. */
    def lsBits(n: Int = 1): UInt = c(n - 1, 0)

    /** returns the most significant bit as Bool. */
    def msBit: Bool = c.head(1).asBool

    /** returns the least significant bit as Bool. */
    def lsBit: Bool = c(0).asBool

    /** sets all the bits to the specified Bool literal. */
    def setAllTo(b: Bool): T = {
      c := Fill(c.getWidth, b).asTypeOf(c)
      c
    }

    /** sets all the bits to the specified Boolean literal. */
    def setAllTo(b: Boolean): T = setAllTo(b.B)

    /** sets all the bits to True. */
    def setAll(): T = setAllTo(true)

    /** sets all the bits to False. */
    def clearAll(): T = setAllTo(false)

    /** returns whether the number is one-hot encoded. */
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
}
