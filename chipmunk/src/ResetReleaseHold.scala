package chipmunk

import chisel3.*
import chisel3.util.log2Ceil

/** Hold a reset request until a release condition is stable in a control clock domain.
  *
  * `releaseOk` must be synchronous to `controlClock`. A loss of `releaseOk` is observed on the next control clock edge;
  * faults that must assert reset without that clock must also drive `asyncReset`. Connect `resetRequest` to the
  * asynchronous input of a separate [[ResetSync]] in each destination clock domain.
  *
  * @param stableCycles
  *   number of consecutive control clock edges for which `releaseOk` must be high before the request is removed
  */
final class ResetReleaseHold(stableCycles: Int) extends RawModule:
  require(stableCycles >= 1, "ResetReleaseHold requires at least one stable cycle.")

  val io = IO(new Bundle:
    val controlClock = Input(Clock())
    val asyncReset   = Input(AsyncReset())
    val releaseOk    = Input(Bool())
    val resetRequest = Output(AsyncReset())
    val qualified    = Output(Bool()))

  private val qualified = withClockAndReset(io.controlClock, io.asyncReset):
    val consecutiveCycles = RegInit(0.U(math.max(1, log2Ceil(stableCycles)).W))
    val conditionMet      = RegInit(false.B)

    when(!io.releaseOk) {
      consecutiveCycles := 0.U
      conditionMet      := false.B
    }.elsewhen(!conditionMet) {
      when(consecutiveCycles === (stableCycles - 1).U) {
        conditionMet := true.B
      }.otherwise {
        consecutiveCycles := consecutiveCycles + 1.U
      }
    }

    conditionMet

  io.qualified    := qualified
  io.resetRequest := (io.asyncReset.asBool || !qualified).asAsyncReset
