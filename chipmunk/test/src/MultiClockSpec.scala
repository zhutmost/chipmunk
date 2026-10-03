package chipmunk.test

import scala.collection.mutable.ArrayBuffer

import chisel3.*

import chipmunk.tester.{ClockEdge, ClockEvent, ClockSpec}

private object MultiClockSpecDut {
  final class Harness extends Module {
    val io = IO(new Bundle {
      val clockLevels = Input(UInt(2.W))
      val fastCount   = Output(UInt(8.W))
      val slowCount   = Output(UInt(8.W))
    })

    io.fastCount := counter(io.clockLevels(0).asClock)
    io.slowCount := counter(io.clockLevels(1).asClock)

    private def counter(domainClock: Clock): UInt =
      withClockAndReset(domainClock, reset.asAsyncReset) {
        val value = RegInit(0.U(8.W))
        value := value + 1.U
        value
      }
  }
}

class MultiClockSpec extends ChipmunkFlatSpec {
  "MultiClockSupport" should "schedule different frequencies and coalesce simultaneous edges deterministically" in {
    simulate(new MultiClockSpecDut.Harness) { dut =>
      val fast = ClockSpec(name = "fast", periodTicks = 4)
      val slow = ClockSpec(name = "slow", periodTicks = 6, firstRiseAt = 4)

      // The sequence index is the corresponding bit in clockLevels.
      val scheduler =
        new MultiClockScheduler(dut.io.clockLevels, dut.clock, Seq(fast, slow))

      scheduler.initialize()
      dut.io.fastCount expect 0.U
      dut.io.slowCount expect 0.U

      val expectedCounts = Seq((1, 0), (1, 1), (2, 1), (2, 1), (2, 1), (3, 2))
      val events         = ArrayBuffer.empty[ClockEvent]

      scheduler.runEvents(6) { event =>
        val (fastCount, slowCount) = expectedCounts(events.size)
        events.addOne(event)

        dut.io.clockLevels expect event.levels
        dut.io.fastCount expect fastCount.U
        dut.io.slowCount expect slowCount.U
      }

      events.map(_.time).toSeq shouldBe Seq(2L, 4L, 6L, 7L, 8L, 10L)
      events.map(_.levels).toSeq shouldBe
        Seq(1, 2, 3, 1, 0, 3).map(BigInt(_))

      events(1).transitions.map(t => t.domain.name -> t.edge) shouldBe
        Seq("fast" -> ClockEdge.Falling, "slow" -> ClockEdge.Rising)
      events(1).falling.map(_.domain.name) shouldBe Seq("fast")
      events(1).rising.map(_.domain.name) shouldBe Seq("slow")

      events(5).transitions.map(t => t.domain.name -> t.edge) shouldBe
        Seq("fast" -> ClockEdge.Rising, "slow" -> ClockEdge.Rising)

      scheduler.time shouldBe 10L
      scheduler.levels shouldBe BigInt(3)
      scheduler.nextEventTime shouldBe 12L
      dut.io.fastCount expect 3.U
      dut.io.slowCount expect 2.U
    }
  }

  it should "use the configured low duration for the initial and subsequent low phases" in {
    simulate(new MultiClockSpecDut.Harness) { dut =>
      val fast = ClockSpec(name = "fast", periodTicks = 100)
      val slow = ClockSpec(name = "slow", periodTicks = 6, lowTicks = 4)

      val scheduler =
        new MultiClockScheduler(dut.io.clockLevels, dut.clock, Seq(fast, slow))

      val expectedSlowCounts = Seq(1, 1, 2, 2)
      val events             = ArrayBuffer.empty[ClockEvent]

      scheduler.runEvents(4) { event =>
        val slowCount = expectedSlowCounts(events.size)
        events.addOne(event)

        dut.io.clockLevels expect event.levels
        dut.io.fastCount expect 0.U
        dut.io.slowCount expect slowCount.U
      }

      // Initial low: 4 ticks. High: 2 ticks. Later low: 4 ticks.
      events.map(_.time).toSeq shouldBe Seq(4L, 6L, 10L, 12L)
      events.map(_.levels).toSeq shouldBe Seq(2, 0, 2, 0).map(BigInt(_))
      events.map(_.transitions.head.edge).toSeq shouldBe
        Seq(ClockEdge.Rising, ClockEdge.Falling, ClockEdge.Rising, ClockEdge.Falling)

      scheduler.nextEventTime shouldBe 16L
    }
  }
}
