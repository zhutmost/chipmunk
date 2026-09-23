package chipmunk.test

import chipmunk.{RegNegEnable, RegNegNext}
import chisel3.*

private object RegNegSpecDut:

  final class NoResetHarness extends Module:
    val io = IO(new Bundle:
      val clockLevels = Input(UInt(1.W))
      val data        = Input(UInt(8.W))
      val enable      = Input(Bool())
      val nextValue   = Output(UInt(8.W))
      val enabled     = Output(UInt(8.W)))

    io.nextValue := withClock(io.clockLevels(0).asClock):
      RegNegNext(io.data)
    io.enabled := withClock(io.clockLevels(0).asClock):
      RegNegEnable(io.data, io.enable)

  final class AsyncResetHarness extends Module:
    val io = IO(new Bundle:
      val clockLevels = Input(UInt(1.W))
      val domainReset = Input(Bool())
      val data        = Input(UInt(8.W))
      val output      = Output(UInt(8.W)))

    io.output := withClockAndReset(io.clockLevels(0).asClock, io.domainReset.asAsyncReset):
      RegNegNext(io.data, 0xa5.U(8.W), isResetAsync = true)

  final class SyncResetHarness extends Module:
    val io = IO(new Bundle:
      val clockLevels = Input(UInt(1.W))
      val domainReset = Input(Bool())
      val data        = Input(UInt(8.W))
      val output      = Output(UInt(8.W)))

    io.output := withClockAndReset(io.clockLevels(0).asClock, io.domainReset):
      RegNegNext(io.data, 0xa5.U(8.W), isResetAsync = false)

class RegNegSpec extends ChipmunkFlatSpec:

  private def driveClock(clockLevels: UInt, timebaseClock: Clock, high: Boolean): Unit =
    clockLevels #= (if high then 1.U else 0.U)
    timebaseClock.step(0)

  "RegNegNext and RegNegEnable" should "capture only on falling edges and honor enable" in:
    simulate(new RegNegSpecDut.NoResetHarness): dut =>
      dut.io.data #= 0x12.U
      dut.io.enable #= true.B
      driveClock(dut.io.clockLevels, dut.clock, high = false)

      driveClock(dut.io.clockLevels, dut.clock, high = true)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.nextValue.expect(0x12.U)
      dut.io.enabled.expect(0x12.U)

      dut.io.data #= 0x34.U
      driveClock(dut.io.clockLevels, dut.clock, high = true)
      dut.io.nextValue.expect(0x12.U)
      dut.io.enabled.expect(0x12.U)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.nextValue.expect(0x34.U)
      dut.io.enabled.expect(0x34.U)

      dut.io.enable #= false.B
      dut.io.data #= 0x56.U
      driveClock(dut.io.clockLevels, dut.clock, high = true)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.nextValue.expect(0x56.U)
      dut.io.enabled.expect(0x34.U)

  it should "apply asynchronous reset without waiting for a clock edge" in:
    simulate(new RegNegSpecDut.AsyncResetHarness): dut =>
      dut.io.domainReset #= false.B
      dut.io.data #= 0x31.U
      driveClock(dut.io.clockLevels, dut.clock, high = false)

      driveClock(dut.io.clockLevels, dut.clock, high = true)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.output.expect(0x31.U)

      dut.io.domainReset #= true.B
      dut.clock.step(0)
      dut.io.output.expect(0xa5.U)

      dut.io.domainReset #= false.B
      dut.io.data #= 0x52.U
      driveClock(dut.io.clockLevels, dut.clock, high = true)
      dut.io.output.expect(0xa5.U)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.output.expect(0x52.U)

  it should "apply synchronous reset on the next falling edge" in:
    simulate(new RegNegSpecDut.SyncResetHarness): dut =>
      dut.io.domainReset #= false.B
      dut.io.data #= 0x31.U
      driveClock(dut.io.clockLevels, dut.clock, high = false)

      driveClock(dut.io.clockLevels, dut.clock, high = true)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.output.expect(0x31.U)

      dut.io.domainReset #= true.B
      dut.clock.step(0)
      dut.io.output.expect(0x31.U)

      driveClock(dut.io.clockLevels, dut.clock, high = true)
      dut.io.output.expect(0x31.U)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.output.expect(0xa5.U)

      dut.io.domainReset #= false.B
      dut.io.data #= 0x73.U
      driveClock(dut.io.clockLevels, dut.clock, high = true)
      driveClock(dut.io.clockLevels, dut.clock, high = false)
      dut.io.output.expect(0x73.U)
