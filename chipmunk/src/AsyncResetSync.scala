package chipmunk

import chisel3.*
import chisel3.util.ShiftRegister

/** Synchronize the deassertion of an active-high asynchronous reset.
  *
  * Assertion of the implicit reset asserts `resetOut` asynchronously. Deassertion propagates through `stages` registers
  * on the implicit clock. After assertion, `resetOut` remains asserted while that clock is stopped.
  *
  * @param stages
  *   number of synchronization stages; must be at least two
  */
final class AsyncResetSync(val stages: Int = 2) extends Module with RequireAsyncReset {
  require(stages >= 2, "AsyncResetSync requires at least two stages.")

  val io = IO(new Bundle {
    val resetOut = Output(AsyncReset())
  })

  private val resetSynced = ShiftRegister(false.B, stages, true.B, true.B)

  io.resetOut := resetSynced.asAsyncReset
}

object AsyncResetSync {

  /** Synchronize the implicit reset to the implicit clock using [[AsyncResetSync]]. Note that only high-active reset is
    * supported.
    *
    * @param stages
    *   The stage number of the reset synchronizer buffer.
    * @return
    *   The synchronized reset signal.
    */
  def apply(stages: Int = 2): AsyncReset = {
    val uResetSync = Module(new AsyncResetSync(stages))
    uResetSync.io.resetOut
  }

  /** Synchronize the given reset to the given clock using [[AsyncResetSync]]. Note that only high-active reset is
    * supported.
    *
    * @param clock
    *   The target clock.
    * @param resetAsync
    *   The input asynchronous reset signal needed to be synchronized with the target clock.
    * @param stages
    *   The stage number of the synchronizer.
    * @return
    *   The synchronized reset signal.
    */
  def withSpecificClockDomain(clock: Clock, resetAsync: Reset, stages: Int = 2): AsyncReset = {
    withClockAndReset(clock, resetAsync.asAsyncReset) {
      apply(stages)
    }
  }
}
