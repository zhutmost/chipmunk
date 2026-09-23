package chipmunk.test

import chipmunk.tester.MultiClockSupport.{ClockDomain, ClockEvent, Edge}
import chisel3.*

import scala.collection.mutable.ArrayBuffer

private object MultiClockSpecDut:

  final class Harness extends Module:
    val io = IO(new Bundle:
      val clockLevels = Input(UInt(2.W))
      val fastCount   = Output(UInt(8.W))
      val slowCount   = Output(UInt(8.W)))

    io.fastCount := counter(io.clockLevels(0).asClock)
    io.slowCount := counter(io.clockLevels(1).asClock)

    private def counter(domainClock: Clock): UInt =
      withClockAndReset(domainClock, reset.asAsyncReset):
        val value = RegInit(0.U(8.W))
        value := value + 1.U
        value

class MultiClockSpec extends ChipmunkFlatSpec:

  "MultiClockSupport" should "schedule different frequencies and coalesce simultaneous edges deterministically" in:
    simulate(new MultiClockSpecDut.Harness): dut =>
      val fast      = ClockDomain(name = "fast", bit = 0, halfPeriod = 2)
      val slow      = ClockDomain(name = "slow", bit = 1, halfPeriod = 3, phase = 1)
      val scheduler = multiClockScheduler(dut.io.clockLevels, dut.clock, Seq(slow, fast))

      scheduler.initialize()
      dut.io.fastCount.expect(0.U)
      dut.io.slowCount.expect(0.U)

      val expectedCounts = Seq((1, 0), (1, 1), (2, 1), (2, 1), (2, 1), (3, 2))
      val events         = ArrayBuffer.empty[ClockEvent]
      scheduler.run(6): event =>
        val (fastCount, slowCount) = expectedCounts(events.size)
        events.addOne(event)
        dut.io.clockLevels.expect(event.levels)
        dut.io.fastCount.expect(fastCount.U)
        dut.io.slowCount.expect(slowCount.U)
        ()

      events.map(_.time).toSeq shouldBe Seq(2, 4, 6, 7, 8, 10)
      events.map(_.levels).toSeq shouldBe Seq(1, 2, 3, 1, 0, 3).map(value => BigInt(value))

      events(1).transitions.map(transition => transition.domain.name -> transition.edge) shouldBe
        Seq("fast" -> Edge.Falling, "slow" -> Edge.Rising)
      events(1).falling.map(_.domain.name) shouldBe Seq("fast")
      events(1).rising.map(_.domain.name) shouldBe Seq("slow")
      events(5).transitions.map(transition => transition.domain.name -> transition.edge) shouldBe
        Seq("fast" -> Edge.Rising, "slow" -> Edge.Rising)

      scheduler.time shouldBe 10
      scheduler.levels shouldBe BigInt(3)
      scheduler.nextEventTime shouldBe 12
      dut.io.fastCount.expect(3.U)
      dut.io.slowCount.expect(2.U)
