package chipmunk
package stream

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.log2Ceil
import chisel3.util.random.LFSR

import chipmunk.{goto, on, Master, Slave, StateMachine}

/** Fixed and pseudorandom delays for StreamIO transactions. */
object StreamDelay {

  /** Delay each transaction's output presentation by `cycles` clock cycles.
    *
    * Input is held until output transfers; consumer stalls may extend the transfer delay. Zero returns `in` unchanged.
    */
  def fixed[T <: Data](in: StreamIO[T], cycles: Int)(using SourceInfo): StreamIO[T] = {
    require(cycles >= 0, "Delay cycles must be nonnegative.")

    if (cycles == 0) {
      in
    } else {
      val delay = Module(new StreamDelay(chiselTypeOf(in.bits), log2Ceil(BigInt(cycles) + 1)))
      delay.io.in << in
      delay.io.targetDelay := cycles.U
      delay.io.out
    }
  }

  /** Choose a pseudorandom presentation delay in the inclusive range `[minCycles, maxCycles]`.
    *
    * A new delay is selected for each transaction, including zero-delay transfers. The sequence repeats after reset and
    * its distribution is not necessarily uniform. Consumer stalls may extend the transfer delay.
    */
  def random[T <: Data](in: StreamIO[T], maxCycles: Int, minCycles: Int = 0)(using SourceInfo): StreamIO[T] = {
    require(minCycles >= 0, "Minimum delay must be nonnegative.")
    require(maxCycles >= minCycles, "Maximum delay must be at least the minimum delay.")

    if (maxCycles == minCycles) {
      fixed(in, maxCycles)
    } else {
      val delay       = Module(new StreamDelay(chiselTypeOf(in.bits), log2Ceil(BigInt(maxCycles) + 1)))
      val choices     = BigInt(maxCycles) - minCycles + 1
      val randomWidth = math.max(2, log2Ceil(choices + 1))
      val value       = LFSR(randomWidth, increment = delay.io.targetDelayUpdate, seed = Some(BigInt(1)))

      // XOR LFSRs exclude zero. Subtract one so the minimum delay is reachable.
      // The chosen width keeps candidate below 2 * choices, so one fold suffices.
      val candidate = value - 1.U
      val offset    = Mux(candidate >= choices.U, candidate - choices.U, candidate)

      delay.io.in << in
      delay.io.targetDelay := offset +& minCycles.U
      delay.io.out
    }
  }
}

/** Delay output presentation without buffering payload or accepting input early.
  *
  * A transaction first offered while idle in cycle `k` is presented at output in cycle `k + targetDelay`. The delay is
  * sampled once per transaction; later changes affect the next transaction. Zero permits same-cycle transfer. The
  * producer must hold `valid`/`bits` until transfer, including during the delay. Reset returns to idle and restarts the
  * delay of any still-offered transaction.
  *
  * @param gen
  *   payload type
  * @param delayWidth
  *   width of the unsigned delay in cycles; must be positive
  */
class StreamDelay[T <: Data](gen: T, delayWidth: Int) extends Module {
  require(delayWidth >= 1, "Delay width must be positive.")

  val io = IO(new Bundle {
    val in  = Slave(Stream(gen))
    val out = Master(Stream(gen))

    /** Delay for the next transaction; sampled while idle when input is valid. */
    val targetDelay = Input(UInt(delayWidth.W))

    /** Pulse when a transaction's delay is selected, including zero and one cycle. */
    val targetDelayUpdate = Output(Bool())
  })

  private object States extends ChiselEnum {
    val Idle, Counting, Pending = Value
  }

  // Loaded before entering Counting; its reset value is never observed.
  private val remaining = Reg(UInt(delayWidth.W))

  io.out.bits          := io.in.bits
  io.out.valid         := false.B
  io.in.ready          := io.out.fire
  io.targetDelayUpdate := false.B

  StateMachine(States)(States.Idle) {
    on(States.Idle) {
      io.out.valid := io.in.valid && io.targetDelay === 0.U

      when(io.in.valid) {
        io.targetDelayUpdate := true.B

        when(io.targetDelay === 0.U) {
          when(!io.out.ready) {
            goto(States.Pending)
          }
        }.elsewhen(io.targetDelay === 1.U) {
          goto(States.Pending)
        }.otherwise {
          remaining := io.targetDelay - 1.U
          goto(States.Counting)
        }
      }
    }

    on(States.Counting) {
      when(remaining === 1.U) {
        goto(States.Pending)
      }.otherwise {
        remaining := remaining - 1.U
      }
    }

    on(States.Pending) {
      io.out.valid := true.B
      when(io.out.fire) {
        goto(States.Idle)
      }
    }
  }
}
