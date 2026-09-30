package chipmunk
package tester

import chisel3.{Clock, UInt}
import chisel3.simulator.PeekPokeAPI

/** Timing of one simulated clock.
  *
  * All clocks initially stay low. By default, the low phase lasts `periodTicks / 2` ticks, and the first rising edge
  * occurs when that initial low phase ends. For an odd period, the high phase gets the extra tick.
  *
  * `-1` means "use the default" for `firstRiseAt` and `lowTicks`. The scheduler uses the resolved fields below.
  *
  * @param name
  *   stable name used to identify this clock in events
  * @param periodTicks
  *   number of logical ticks between consecutive rising edges
  * @param firstRiseAt
  *   timestamp of the first rising edge, or `-1` for the default
  * @param lowTicks
  *   duration of each low phase, or `-1` for the default
  */
final case class ClockSpec(name: String, periodTicks: Long, firstRiseAt: Long = -1L, lowTicks: Long = -1L) {
  require(name.nonEmpty, "clock name must not be empty")
  require(periodTicks >= 2, s"clock period must be at least 2 ticks, got $periodTicks")
  require(
    lowTicks == -1L || (lowTicks > 0 && lowTicks < periodTicks),
    s"low duration must be between 1 and ${periodTicks - 1}, got $lowTicks",
  )
  require(firstRiseAt == -1L || firstRiseAt > 0, s"first rising edge must occur after time zero, got $firstRiseAt")

  /** Low-phase duration after resolving the optional default. */
  val effectiveLowTicks: Long =
    if lowTicks == -1L then periodTicks / 2 else lowTicks

  /** High-phase duration. */
  val highTicks: Long = periodTicks - effectiveLowTicks

  /** First rising-edge timestamp after resolving the optional default. */
  val effectiveFirstRiseAt: Long =
    if firstRiseAt == -1L then effectiveLowTicks
    else firstRiseAt
}

/** Direction of a simulated clock edge. */
enum ClockEdge {
  case Rising, Falling
}

/** One clock edge within a scheduler event. */
final case class ClockTransition(domain: ClockSpec, edge: ClockEdge)

/** All clock edges occurring at one logical timestamp.
  *
  * `transitions` follows the order of the clock specifications supplied to the scheduler.
  */
final case class ClockEvent(time: Long, transitions: Vector[ClockTransition], levels: BigInt) {
  def rising: Vector[ClockTransition] =
    transitions.filter(_.edge == ClockEdge.Rising)

  def falling: Vector[ClockTransition] =
    transitions.filter(_.edge == ClockEdge.Falling)
}

/** Deterministic multi-clock stimulus for ChiselSim.
  *
  * The test harness exposes clock levels as a packed `UInt` input and converts each bit to a DUT clock with `.asClock`.
  * The first supplied clock uses bit 0, the second uses bit 1, and so on.
  */
trait MultiClockSupport {
  this: PeekPokeAPI =>

  /** Drives the packed clock input according to the supplied clocks.
    *
    * `timebaseClock` only advances simulation time. It must not clock state in the DUT. One logical tick corresponds to
    * two simulator timesteps.
    *
    * The scheduler can be constructed inside a test that mixes in `MultiClockSupport`:
    *
    * {{{
    * val scheduler = new MultiClockScheduler(
    *   dut.io.clockLevels,
    *   dut.io.timebaseClock,
    *   Seq(
    *     ClockSpec("read", periodTicks = 4),
    *     ClockSpec("write", periodTicks = 6, firstRiseAt = 2, lowTicks = 2),
    *   ),
    * )
    * }}}
    */
  final class MultiClockScheduler(clockLevels: UInt, timebaseClock: Clock, domains: Seq[ClockSpec]) {
    private val specs = domains.toVector

    require(specs.nonEmpty, "at least one clock is required")
    require(specs.map(_.name).distinct.size == specs.size, "clock names must be unique")
    require(
      clockLevels.getWidth == specs.size,
      s"clock-level port has width ${clockLevels.getWidth}, " +
        s"but ${specs.size} clocks were supplied",
    )

    private val plan        = new ClockPlan(specs)
    private var initialized = false

    /** Current logical timestamp. */
    def time: Long = plan.time

    /** Current packed clock levels. All bits are initially low. */
    def levels: BigInt = plan.levels

    /** Timestamp of the next set of clock edges. */
    def nextEventTime: Long = plan.nextEventTime

    /** Drives all clocks low and evaluates the harness at time zero.
      *
      * Calling this more than once has no additional effect. `advance()` calls it automatically if necessary.
      */
    def initialize(): Unit =
      if !initialized then {
        toTestableUInt(clockLevels).poke(BigInt(0))
        toTestableClock(timebaseClock).step(0)
        initialized = true
      }

    /** Advances to the next timestamp with at least one clock edge.
      *
      * All edges at that timestamp are applied with one packed `poke`. Returns after the resulting circuit state has
      * been evaluated.
      */
    def advance(): ClockEvent = {
      initialize()

      val targetTime = plan.nextEventTime
      waitTicks(Math.subtractExact(targetTime, plan.time))

      val event = plan.advance()
      toTestableUInt(clockLevels).poke(event.levels)
      toTestableClock(timebaseClock).step(0)
      event
    }

    /** Processes exactly `eventCount` edge timestamps.
      *
      * An event may contain edges from several clocks.
      */
    def runEvents(eventCount: Int)(observe: ClockEvent => Unit = _ => ()): Unit = {
      require(eventCount >= 0, s"event count must be non-negative, got $eventCount")
      for _ <- 0 until eventCount do observe(advance())
    }

    /** Advances time in chunks whose doubled period fits in `Int`. */
    private def waitTicks(ticks: Long): Unit = {
      require(ticks > 0, s"next clock event must be in the future, got $ticks ticks")

      val maxChunkTicks = Int.MaxValue.toLong / 2
      var remaining     = ticks

      while remaining > 0 do {
        val chunk = math.min(remaining, maxChunkTicks).toInt
        toTestableClock(timebaseClock).step(cycles = 1, period = chunk * 2)
        remaining -= chunk
      }
    }
  }

  /** Calculates clock events without accessing ChiselSim. */
  private final class ClockPlan(specs: Vector[ClockSpec]) {
    private final class DomainState(val spec: ClockSpec, val bit: Int) {
      var high: Boolean    = false
      var nextEdgeAt: Long = spec.effectiveFirstRiseAt
    }

    private val states =
      specs.zipWithIndex.map { case (spec, bit) =>
        new DomainState(spec, bit)
      }

    private var currentTime: Long = 0L
    private var packedLevels      = BigInt(0)

    def time: Long = currentTime

    def levels: BigInt = packedLevels

    def nextEventTime: Long =
      states.iterator.map(_.nextEdgeAt).min

    def advance(): ClockEvent = {
      val eventTime = nextEventTime
      require(eventTime > currentTime, "clock plan did not advance in time")

      val active = states.filter(_.nextEdgeAt == eventTime)

      // Calculate every following edge before changing any state.
      val updates = active.map { state =>
        val becomesHigh = !state.high
        val duration    =
          if becomesHigh then state.spec.highTicks
          else state.spec.effectiveLowTicks

        val followingEdgeAt = Math.addExact(eventTime, duration)
        (state, becomesHigh, followingEdgeAt)
      }

      val transitions = updates.map { case (state, becomesHigh, followingEdgeAt) =>
        state.high = becomesHigh
        state.nextEdgeAt = followingEdgeAt

        packedLevels =
          if becomesHigh then packedLevels.setBit(state.bit)
          else packedLevels.clearBit(state.bit)

        ClockTransition(state.spec, if becomesHigh then ClockEdge.Rising else ClockEdge.Falling)
      }

      currentTime = eventTime
      ClockEvent(eventTime, transitions, packedLevels)
    }
  }
}
