package chipmunk
package tester

import chisel3.{Clock, UInt}
import chisel3.simulator.PeekPokeAPI

object MultiClockSupport:

  /** Edge direction produced by a simulated clock transition. */
  enum Edge:
    case Rising, Falling

  /** Configuration of one clock domain.
    *
    * All clocks start low. The first rising edge occurs after `phase + halfPeriod` logical ticks; subsequent edges are
    * separated by `halfPeriod` ticks.
    *
    * @param name
    *   stable name used in returned events
    * @param bit
    *   bit position in the packed clock-level input
    * @param halfPeriod
    *   number of logical ticks between adjacent edges
    * @param phase
    *   additional delay before this domain starts
    */
  final case class ClockDomain(name: String, bit: Int, halfPeriod: Int, phase: Int = 0):
    require(name.nonEmpty, "clock-domain name must not be empty")
    require(bit >= 0, s"clock-domain bit must be non-negative, got $bit")
    require(halfPeriod > 0, s"clock-domain half-period must be positive, got $halfPeriod")
    require(phase >= 0, s"clock-domain phase must be non-negative, got $phase")

  /** One clock transition within a scheduler event. */
  final case class Transition(domain: ClockDomain, edge: Edge)

  /** All clock transitions occurring at one logical timestamp. */
  final case class ClockEvent(time: Int, transitions: Seq[Transition], levels: BigInt):
    def rising: Seq[Transition]  = transitions.filter(_.edge == Edge.Rising)
    def falling: Seq[Transition] = transitions.filter(_.edge == Edge.Falling)

/** Deterministic multi-clock support for ChiselSim.
  *
  * A harness exposes all DUT clock levels as one packed `UInt` input and converts individual bits to clocks with
  * `.asClock`. Updating the packed value makes edges at the same timestamp visible to the simulator in one evaluation.
  */
trait MultiClockSupport:
  this: PeekPokeAPI =>

  import MultiClockSupport.*

  /** Create a deterministic scheduler for clocks represented by `clockLevels`.
    *
    * `timebaseClock` is used only to advance simulator time. It must not clock state in the DUT. One logical scheduler
    * tick corresponds to two simulator timesteps; this allows every positive logical interval to be represented by a
    * legal ChiselSim clock period.
    */
  final def multiClockScheduler(
    clockLevels: UInt,
    timebaseClock: Clock,
    domains: Seq[ClockDomain]
  ): MultiClockScheduler = new MultiClockScheduler(clockLevels, timebaseClock, domains)

  final class MultiClockScheduler private[tester] (clockLevels: UInt, timebaseClock: Clock, domains: Seq[ClockDomain]):

    require(domains.nonEmpty, "at least one clock domain is required")
    require(domains.map(_.name).distinct.size == domains.size, "clock-domain names must be unique")
    require(domains.map(_.bit).distinct.size == domains.size, "clock-domain bit positions must be unique")
    require(
      domains.map(_.bit).max < clockLevels.getWidth,
      s"clock-domain bit does not fit in ${clockLevels.getWidth}-bit clock-level port"
    )

    private final class DomainState(val domain: ClockDomain):
      var high: Boolean = false
      var nextEdge: Int = Math.addExact(domain.phase, domain.halfPeriod)

    private val states       = domains.sortBy(_.bit).map(domain => new DomainState(domain))
    private var initialized  = false
    private var logicalTime  = 0
    private var packedLevels = BigInt(0)

    /** Current logical timestamp. */
    def time: Int = logicalTime

    /** Current packed clock levels. */
    def levels: BigInt = packedLevels

    /** Timestamp of the next scheduled clock event. */
    def nextEventTime: Int = states.map(_.nextEdge).min

    /** Drive every managed clock low and evaluate the harness without advancing time. */
    def initialize(): Unit =
      if !initialized then
        toTestableUInt(clockLevels).poke(packedLevels)
        toTestableClock(timebaseClock).step(0)
        initialized = true

    /** Advance to and apply the next set of simultaneous clock edges. */
    def advance(): ClockEvent =
      initialize()

      val eventTime = nextEventTime
      val elapsed   = Math.subtractExact(eventTime, logicalTime)
      require(elapsed >= 0, "multi-clock scheduler moved backwards")

      if elapsed > 0 then
        val period = Math.multiplyExact(elapsed, 2)
        toTestableClock(timebaseClock).step(cycles = 1, period = period)
        logicalTime = eventTime

      val activeStates = states.filter(_.nextEdge == eventTime)
      val transitions  = activeStates.map { state =>
        state.high = !state.high
        packedLevels =
          if state.high then packedLevels.setBit(state.domain.bit)
          else packedLevels.clearBit(state.domain.bit)
        state.nextEdge = Math.addExact(state.nextEdge, state.domain.halfPeriod)
        Transition(state.domain, if state.high then Edge.Rising else Edge.Falling)
      }

      toTestableUInt(clockLevels).poke(packedLevels)
      toTestableClock(timebaseClock).step(0)

      ClockEvent(logicalTime, transitions, packedLevels)

    /** Process exactly `eventCount` scheduler timestamps. */
    def run(eventCount: Int)(observe: ClockEvent => Unit = _ => ()): Unit =
      require(eventCount >= 0, s"event count must be non-negative, got $eventCount")
      for _ <- 0 until eventCount do observe(advance())
