import chisel3._
import chisel3.util._

package object chipmunk {
  implicit class AddMethodsToBits[T <: Bits](c: T) {

    /** returns the most significant n bits. */
    def msb(n: Int = 1): UInt = c.head(n)

    /** returns the least significant n bits. */
    def lsb(n: Int = 1): UInt = c(n - 1, 0)

    /** sets all the bits to the specified Bool input. */
    def setAllTo(b: Bool): T = {
      c := Fill(c.getWidth, b)
      c
    }

    /** sets all the bits to the specified Boolean literal. */
    def setAllTo(b: Boolean): T = setAllTo(b.B)

    /** sets all the bits to True. */
    def setAll(): T = setAllTo(true)

    /** sets all the bits to False. */
    def clearAll(): T = setAllTo(false)
  }
}
