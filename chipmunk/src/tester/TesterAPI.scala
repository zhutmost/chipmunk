package chipmunk
package tester

import chisel3._
import chisel3.simulator._

trait TesterAPI {
  self: PeekPokeAPI =>

  implicit class DataTestablePortExtension[T <: Data](target: T)(implicit toTestable: T => Pokable[T]) {

    /** Assign a Chisel literal to the port. */
    def #=(value: T): Unit = toTestable(target).poke(value)
  }

  implicit class BoolTestablePortExtension(bool: Bool) {

    /** Assign a random value to the port. */
    def randomize(): Unit = {
      bool.poke(scala.util.Random.nextBoolean().B)
    }
  }

  implicit final class UIntTestablePortExtension(uint: UInt)(implicit toTestable: UInt => Pokable[UInt]) {

    /** Assign a random value to the port. */
    def randomize(): Unit = {
      uint.poke(BigInt(uint.getWidth, scala.util.Random).U)
    }
  }

  implicit final class SIntTestablePortExtension(sint: SInt)(implicit toTestable: SInt => Pokable[SInt]) {

    /** Assign a random value to the port. */
    def randomize(): Unit = {
      sint.poke((BigInt(sint.getWidth, scala.util.Random) - (BigInt(1) << (sint.getWidth - 1))).S)
    }
  }
}
