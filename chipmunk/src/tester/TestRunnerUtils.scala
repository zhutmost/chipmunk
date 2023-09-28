package chipmunk
package tester

import chisel3._
import svsim._

object TestRunnerUtils {
  import chisel3.simulator.PeekPokeAPI._

  implicit final class TestableClock(clock: Clock) extends testableClock(clock)

  implicit final class TestableDataPoker[T <: Data](data: Data) {

    /** Assign a [[BigInt]] literal to the port. */
    def set(value: BigInt): Unit = data.poke(value)

    /** Assign a [[BigInt]] literal to the port. */
    def #=(value: BigInt): Unit = set(value)

    /** Assign a [[Boolean]] literal to the port. */
    def set(boolean: Boolean): Unit = data.poke(boolean)

    /** Assign a [[Boolean]] literal to the port. */
    def #=(boolean: Boolean): Unit = set(boolean)

    /** Assign a hardware literal to the port. */
    def set(literal: T): Unit = {
      assert(literal.isLit, "Only hardware literals can be assigned to a port")
      data.poke(literal)
    }

    /** Assign a hardware literal to the port. */
    def #=(literal: T): Unit = set(literal)

    /** Assign a random value to the port. */
    def randomize(): Unit = data match {
      case _: Bool => set(scala.util.Random.nextBoolean())
      case _: SInt => set(BigInt(data.getWidth, scala.util.Random) - (BigInt(1) << (data.getWidth - 1)))
      case _: UInt => set(BigInt(data.getWidth, scala.util.Random))
      case _       => throw new Exception("Unsupported type")
    }
  }

  trait TestableDataPeeker[T <: Data] {
    val data: T

    /** Gets the current value of the port, and returns it as a [[Simulation.Value]]. */
    def getValue(): Simulation.Value = data.peekValue()

    /** Gets the current value of the port, and returns it as a hardware literal. */
    def get(): T

    /** Asserts that the current value of the port is equal to the given [[BigInt]] literal. */
    def expect(value: BigInt): Unit = {
      val observed = getValue().asBigInt
      assert(observed == value, s"Expected failed: observed $observed, but expected $value")
    }

    /** Asserts that the current value of the port is equal to the given hardware literal. */
    def expect(literal: T): Unit = {
      assert(literal.isLit, "Expected value is not a hardware literal")
      assert(
        get().litValue == literal.litValue,
        s"Expected failed: observed ${get().litValue}, but expected ${literal.litValue}"
      )
    }
  }

  implicit final class TestableSIntPeeker(val data: SInt) extends TestableDataPeeker[SInt] {
    def get(): SInt = data.peek()
  }

  implicit final class TestableUIntPeeker(val data: UInt) extends TestableDataPeeker[UInt] {
    def get(): UInt = data.peek()
  }

  implicit final class TestableBoolPeeker(val data: Bool) extends TestableDataPeeker[Bool] {
    def get(): Bool = data.peek()

    /** Asserts that the current value of the port is equal to the given [[Boolean]] literal. */
    def expect(boolean: Boolean): Unit = {
      val litVal = get().litValue
      assert(litVal.isValidByte, s"Expected failed: Expected a boolean but got $litVal")
      val observed = litVal.byteValue match {
        case 0 => false
        case 1 => true
        case _ => throw new Exception(s"Expected failed: Expected a boolean but got $litVal")
      }
      assert(observed == boolean, s"Expected failed: observed $observed, but expected $boolean")
    }
  }
}
