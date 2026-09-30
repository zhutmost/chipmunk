package chipmunk

import chisel3.*

/** Asynchronously assert a reset and release it on the destination clock.
  *
  * The asynchronous input sets every stage. After it is deasserted, zero propagates through `stages` destination
  * clock edges before the output reset is released. The destination clock does not need to run for assertion.
  *
  * Instantiate one synchronizer for each clock/reset domain and share its output within that domain. Physical
  * implementations must identify the stages as a reset synchronizer and apply the appropriate CDC/RDC constraints.
  */
final class ResetSync(stages: Int = 2) extends RawModule:
  require(stages >= 2, "ResetSync requires at least two synchronization stages.")

  val io = IO(new Bundle:
    val clock      = Input(Clock())
    val asyncReset = Input(AsyncReset())
    val resetOut   = Output(AsyncReset())
    val released   = Output(Bool()))

  private val releaseStages = withClockAndReset(io.clock, io.asyncReset):
    val registers = Seq.fill(stages)(RegInit(true.B))
    registers.head := false.B
    for index <- 1 until stages do registers(index) := registers(index - 1)
    registers

  private val resetActive = releaseStages.last
  io.resetOut := resetActive.asAsyncReset
  io.released := !resetActive
