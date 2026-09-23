package chipmunk
package tester

import chisel3.*
import chisel3.simulator.*

/** Small convenience extensions built on top of ChiselSim's peek/poke API.
  */
trait TesterAPI:
  this: PeekPokeAPI =>

  extension [T <: Data](target: T)

    /** Assign a Chisel literal to a DUT port. */
    def #=(value: T): Unit =
      (target, value) match
        case (reset: AsyncReset, literal: Reset) => toTestableReset(reset).poke(literal)
        case _                                   => toTestableData(target).poke(value)

  extension (bool: Bool)

    /** Assign a random value to a `Bool` port. */
    def randomize(): Unit = toTestableBool(bool).poke(scala.util.Random.nextBoolean())

  extension (uint: UInt)

    /** Assign a uniformly distributed random bit pattern to a `UInt` port. */
    def randomize(): Unit = toTestableUInt(uint).poke(BigInt(uint.getWidth, scala.util.Random))

  extension (sint: SInt)

    /** Assign a uniformly distributed two's-complement bit pattern to a `SInt` port. */
    def randomize(): Unit =
      require(sint.getWidth > 0, "cannot randomize a zero-width SInt")
      val value = BigInt(sint.getWidth, scala.util.Random) - (BigInt(1) << (sint.getWidth - 1))
      toTestableSInt(sint).poke(value)
