package chipmunk.test

import chipmunk.{ResetReleaseHold, ResetSync}
import chisel3.*

private object ResetSpecDut:

  final class SyncHarness extends Module:
    val io = IO(new Bundle:
      val clockLevels = Input(UInt(1.W))
      val asyncReset  = Input(Bool())
      val resetActive = Output(Bool())
      val released    = Output(Bool()))

    private val sync = Module(new ResetSync(stages = 2))
    sync.io.clock      := io.clockLevels(0).asClock
    sync.io.asyncReset := io.asyncReset.asAsyncReset
    io.resetActive     := sync.io.resetOut.asBool
    io.released        := sync.io.released

  final class QualifiedHarness extends Module:
    val io = IO(new Bundle:
      val clockLevels = Input(UInt(2.W))
      val asyncReset  = Input(Bool())
      val releaseOk   = Input(Bool())
      val qualified   = Output(Bool())
      val request     = Output(Bool())
      val resetActive = Output(Bool())
      val released    = Output(Bool()))

    private val hold = Module(new ResetReleaseHold(stableCycles = 3))
    hold.io.controlClock := io.clockLevels(0).asClock
    hold.io.asyncReset   := io.asyncReset.asAsyncReset
    hold.io.releaseOk    := io.releaseOk

    private val sync = Module(new ResetSync(stages = 2))
    sync.io.clock      := io.clockLevels(1).asClock
    sync.io.asyncReset := hold.io.resetRequest

    io.qualified   := hold.io.qualified
    io.request     := hold.io.resetRequest.asBool
    io.resetActive := sync.io.resetOut.asBool
    io.released    := sync.io.released

class ResetSpec extends ChipmunkFlatSpec:

  "ResetSync" should "assert while the destination clock is stopped and release after two rising edges" in:
    simulate(new ResetSpecDut.SyncHarness): dut =>
      def drive(level: Boolean): Unit =
        dut.io.clockLevels #= (if level then 1.U else 0.U)
        dut.clock.step(0)

      def tick(): Unit =
        drive(true)
        drive(false)

      dut.io.asyncReset #= true.B
      drive(false)
      dut.io.resetActive.expect(true.B)
      dut.io.released.expect(false.B)

      dut.io.asyncReset #= false.B
      dut.clock.step(0)
      dut.io.resetActive.expect(true.B)
      tick()
      dut.io.resetActive.expect(true.B)
      tick()
      dut.io.resetActive.expect(false.B)
      dut.io.released.expect(true.B)

      dut.io.asyncReset #= true.B
      dut.clock.step(0)
      dut.io.resetActive.expect(true.B)
      dut.io.released.expect(false.B)
      dut.io.asyncReset #= false.B
      dut.clock.step(0)
      dut.io.resetActive.expect(true.B)
      tick()
      tick()
      dut.io.released.expect(true.B)

  "ResetReleaseHold" should "restart qualification and asynchronously reset a stopped destination domain" in:
    simulate(new ResetSpecDut.QualifiedHarness): dut =>
      def drive(levels: Int): Unit =
        dut.io.clockLevels #= levels.U
        dut.clock.step(0)

      def controlTick(): Unit =
        drive(1)
        drive(0)

      def destinationTick(): Unit =
        drive(2)
        drive(0)

      dut.io.asyncReset #= true.B
      dut.io.releaseOk #= false.B
      drive(0)
      dut.io.request.expect(true.B)
      dut.io.resetActive.expect(true.B)

      dut.io.asyncReset #= false.B
      dut.io.releaseOk #= true.B
      controlTick()
      controlTick()
      dut.io.qualified.expect(false.B)
      destinationTick()
      dut.io.resetActive.expect(true.B)

      dut.io.releaseOk #= false.B
      controlTick()
      dut.io.releaseOk #= true.B
      controlTick()
      controlTick()
      dut.io.request.expect(true.B)
      controlTick()
      dut.io.qualified.expect(true.B)
      dut.io.request.expect(false.B)
      dut.io.resetActive.expect(true.B)

      destinationTick()
      dut.io.resetActive.expect(true.B)
      destinationTick()
      dut.io.resetActive.expect(false.B)
      dut.io.released.expect(true.B)

      dut.io.releaseOk #= false.B
      dut.clock.step(0)
      dut.io.resetActive.expect(false.B)
      controlTick()
      dut.io.request.expect(true.B)
      dut.io.resetActive.expect(true.B)
      dut.io.released.expect(false.B)

      dut.io.asyncReset #= true.B
      dut.clock.step(0)
      dut.io.qualified.expect(false.B)
      dut.io.resetActive.expect(true.B)
