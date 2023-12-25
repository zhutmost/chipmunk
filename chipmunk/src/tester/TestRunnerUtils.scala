package chipmunk
package tester

import chisel3._
import svsim._
object TestRunnerUtils extends TestRunnerUtils

trait TestRunnerUtils {

  implicit final class TestableClock(clock: Clock) {
    private def tick(
      maxCycles: Int,
      inPhaseValue: Int,
      outPhaseValue: Int,
      timeStepsPerPhase: Int = 1,
      sentinel: Option[(Data, BigInt)] = None
    ): Unit = {
      assert(maxCycles >= 0, "maxCycles cannot be less than 0")
      val module = AnySimulatedModule.current
      module.willEvaluate()
      if (maxCycles == 0) {
        module.controller.run(0)
      } else {
        val simulationPort = module.port(clock)
        val sentinelOpt = sentinel.map { case (sentinelPort, sentinelValue) =>
          module.port(sentinelPort) -> sentinelValue
        }
        simulationPort.tick(
          timestepsPerPhase = timeStepsPerPhase,
          maxCycles = maxCycles,
          inPhaseValue = inPhaseValue,
          outOfPhaseValue = outPhaseValue,
          sentinel = sentinelOpt
        )
      }
    }

    def step(cycles: Int = 1): Unit = {
      tick(maxCycles = cycles, inPhaseValue = 0, outPhaseValue = 1)
    }

    /** Ticks this clock up to `maxCycles`. Stops early if the `sentinelPort` is equal to the `sentinelValue`.
      */
    def stepUntil(sentinelPort: Data, sentinelValue: BigInt, maxCycles: Int): Unit = {
      tick(maxCycles = maxCycles, inPhaseValue = 0, outPhaseValue = 1, sentinel = Some(sentinelPort -> sentinelValue))
    }

    def stepNeg(cycles: Int = 1): Unit = {
      tick(maxCycles = cycles, inPhaseValue = 1, outPhaseValue = 0)
    }

    def stepNegUntil(sentinelPort: Data, sentinelValue: BigInt, maxCycles: Int): Unit = {
      tick(maxCycles = maxCycles, inPhaseValue = 1, outPhaseValue = 0, sentinel = Some(sentinelPort -> sentinelValue))
    }
  }

  implicit final class TestableDataPoker[T <: Data](data: Data) {

    /** Assign a [[BigInt]] literal to the port. */
    def set(value: BigInt): Unit = {
      val module = AnySimulatedModule.current
      module.willPoke()
      val simulationPort = module.port(data)
      simulationPort.set(value)
    }

    /** Assign a [[BigInt]] literal to the port. */
    def #=(value: BigInt): Unit = set(value)

    /** Assign a [[Boolean]] literal to the port. */
    def set(boolean: Boolean): Unit = set(if (boolean) 1 else 0)

    /** Assign a [[Boolean]] literal to the port. */
    def #=(boolean: Boolean): Unit = set(boolean)

    /** Assign a hardware literal to the port. */
    def set(literal: T): Unit = {
      assert(literal.isLit, "Only hardware literals can be assigned to a port")
      set(literal.litValue)
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

  case class FailedExpectationException[T](observed: T, expected: T)
      extends Exception(s"Failed Expectation: Expected '$expected', but observed $observed.")

  trait TestableDataPeeker[T <: Data] {
    val data: T

    private def isSigned = data.isInstanceOf[SInt]

    /** Gets the current value of the port, and returns it as a [[Simulation.Value]]. */
    def getValue(): Simulation.Value = {
      val module = AnySimulatedModule.current
      module.willPeek()
      val simulationPort = module.port(data)
      simulationPort.get(isSigned = isSigned)
    }

    private[tester] def encode(value: Simulation.Value): T

    /** Gets the current value of the port, and returns it as a hardware literal. */
    def get(): T = encode(getValue())

    /** Asserts that the current value of the port is equal to the given [[BigInt]] literal. */
    def expect(value: BigInt): Unit = {
      val observed = getValue().asBigInt
      if (observed != value) throw FailedExpectationException(observed, value)
    }

    /** Asserts that the current value of the port is equal to the given hardware literal. */
    def expect(literal: T): Unit = {
      assert(literal.isLit, "Expected value is not a hardware literal")
      if (get().litValue != literal.litValue) throw FailedExpectationException(get(), literal)
    }
  }

  implicit final class TestableSIntPeeker(val data: SInt) extends TestableDataPeeker[SInt] {
    override def encode(value: Simulation.Value): SInt = value.asBigInt.asSInt(value.bitCount.W)
  }

  implicit final class TestableUIntPeeker(val data: UInt) extends TestableDataPeeker[UInt] {
    override def encode(value: Simulation.Value): UInt = value.asBigInt.asUInt(value.bitCount.W)
  }

  implicit final class TestableBoolPeeker(val data: Bool) extends TestableDataPeeker[Bool] {
    override def encode(value: Simulation.Value): Bool = {
      if (value.asBigInt.isValidByte) {
        value.asBigInt.byteValue match {
          case 0 => false.B
          case 1 => true.B
          case x => throw new Exception(s"Peeked Bool with value $x, not 0 or 1")
        }
      } else {
        throw new Exception(s"Peeked Bool with value $value, not 0 or 1")
      }
    }

    /** Asserts that the current value of the port is equal to the given [[Boolean]] literal. */
    def expect(boolean: Boolean): Unit = expect(boolean.B)
  }
}
