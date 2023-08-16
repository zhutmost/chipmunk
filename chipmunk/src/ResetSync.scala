package chipmunk

import chisel3._
import chisel3.util._

/** Asynchronous reset synchronizer. Note that only high-active reset is supported.
  *
  * It can synchronize the dessert of the implicit reset signal with the implicit clock. Generally, if an externally
  * input reset signal is to be used for asynchronous reset, it should be synchronized by this module first.
  * {{{
  *                    _______       _______
  *  resetChainIn ---> |D   Q|-...-> |D   Q|---> resetChainOut
  *                    |     |       |     |
  *                    |> R  |       |> R  |
  *                    -------       -------
  *                       |             |
  *  this.reset -------------------------
  * }}}
  *
  * @param bufferDepth
  *   The stage number of the synchronizer.
  */
class ResetSync(val bufferDepth: Int = 2) extends Module with RequireAsyncReset {
  val io = IO(new Bundle {
    val resetChainIn  = Input(Bool())
    val resetChainOut = Output(AsyncReset())
  })

  val resetValue = true.B

  val resetSynced = ShiftRegister(io.resetChainIn, bufferDepth, resetValue, true.B)

  io.resetChainOut := resetSynced.asAsyncReset
}

object AsyncResetSyncDessert {

  /** Synchronize the implicit reset to the implicit clock using [[ResetSync]]. Note that only high-active reset is
    * supported.
    *
    * @param bufferDepth
    *   The stage number of the reset synchronizer buffer.
    * @return
    *   The synchronized reset signal.
    */
  def withImplicitClockDomain(resetChainIn: AsyncReset = null, bufferDepth: Int = 2): AsyncReset = {
    val uRstSync = Module(new ResetSync(bufferDepth))
    if (resetChainIn != null) {
      uRstSync.io.resetChainIn := resetChainIn.asBool
    } else {
      uRstSync.io.resetChainIn := false.B
    }
    uRstSync.io.resetChainOut
  }

  /** Synchronize the given reset to the given clock using [[ResetSync]]. Note that only high-active reset is supported.
    *
    * @param clock
    *   The target clock.
    * @param resetAsync
    *   The input asynchronous reset signal needed to be synchronized with the target clock.
    * @param resetChainIn
    *   The predecessor of reset chain. Only used in some multi-clock scenarios where reset removal must be ordered and
    *   proper sequence. Leave it blank to use VSS.
    * @param bufferDepth
    *   The stage number of the synchronizer.
    * @return
    *   The synchronized reset signal.
    */
  def withSpecificClockDomain(
    clock: Clock,
    resetAsync: Reset,
    resetChainIn: AsyncReset = null,
    bufferDepth: Int = 2
  ): AsyncReset = {
    withClockAndReset(clock, resetAsync.asAsyncReset) {
      withImplicitClockDomain(resetChainIn, bufferDepth)
    }
  }
}
