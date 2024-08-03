package chipmunk
package stream
import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

object StreamDelay {
  def fixed[T <: Data](in: StreamIO[T], cycles: Int): StreamIO[T] = {
    require(cycles >= 0, "The delay cycles must be greater than or equal to 0.")
    if (cycles == 0) {
      in
    } else {
      val uStreamDelay = Module(new StreamDelay(in.bits.cloneType, delayWidth = log2Ceil(cycles + 1)))
      uStreamDelay.io.in << in
      uStreamDelay.io.targetDelay := cycles.U
      uStreamDelay.io.out
    }
  }

  def random[T <: Data](in: StreamIO[T], maxCycles: Int, minCycles: Int = 0): StreamIO[T] = {
    require(minCycles >= 0, "The minimum delay must be greater than or equal to 0.")
    require(maxCycles >= minCycles, "The maximum delay must be greater than or equal to the minimum delay.")
    if (maxCycles == minCycles) {
      fixed(in, maxCycles)
    } else {
      val uStreamDelay = Module(new StreamDelay(in.bits.cloneType, log2Ceil(maxCycles + 1)))

      val randomDelayRange: Int = maxCycles - minCycles - 1
      val randomDelayWidth: Int = log2Ceil(randomDelayRange)
      val lfsrValue: UInt       = LFSR(randomDelayWidth, increment = uStreamDelay.io.targetDelayUpdate)
      val randomDelay: UInt     = Mux(lfsrValue > randomDelayRange.U, lfsrValue - randomDelayRange.U, lfsrValue)
      val targetDelay: UInt     = randomDelay + minCycles.U

      uStreamDelay.io.in << in
      uStreamDelay.io.targetDelay := targetDelay
      uStreamDelay.io.out
    }
  }
}

/** Delay a stream by a fixed/random number of cycles.
  *
  * @param gen
  *   The type of the data in the stream.
  * @param delayWidth
  *   The width of the delay counter. It should be `log2Ceil(maxDelay + 1)`.
  */
class StreamDelay[T <: Data](gen: T, delayWidth: Int) extends Module {
  require(delayWidth >= 1, "The delayWidth must be greater than or equal to 1.")

  val io = IO(new Bundle {
    val in  = Slave(Stream(gen))
    val out = Master(Stream(gen))

    val targetDelay       = Input(UInt(delayWidth.W))
    val targetDelayUpdate = Output(Bool())
  })
  io.out.bits := io.in.bits

  val counterOverflow: Bool = Wire(Bool())

  val fsm = new StateMachine {
    val sIdle  = new State with EntryPoint
    val sCount = new State
    val sPend  = new State
    sIdle.whenIsActive {
      when(io.in.valid) {
        goto(sCount)
        when(io.targetDelay === 0.U) {
          when(io.out.ready) {
            goto(sIdle)
          }.otherwise {
            goto(sPend)
          }
        }.elsewhen(io.targetDelay === 1.U) {
          goto(sPend)
        }
      }
    }
    sCount.whenIsActive {
      when(counterOverflow) {
        goto(sPend)
      }
    }
    sPend.whenIsActive {
      when(io.out.ready) {
        goto(sIdle)
      }
    }
  }
  import fsm.{sIdle, sCount, sPend}

  val counterInc: Bool      = fsm.isActive(sCount)
  val counterReset: Bool    = fsm.isActive(sIdle) || fsm.isEntering(sPend)
  val counter: (UInt, Bool) = Counter(1 until (1 << delayWidth), enable = counterInc, reset = counterReset)
  counterOverflow := counter._1 === io.targetDelay - 1.U

  io.targetDelayUpdate := fsm.isActive(sCount) && counterOverflow

  io.out.valid := fsm.isActive(sPend) || (fsm.isActive(sIdle) && io.targetDelay === 0.U && io.in.valid)
  io.in.ready  := io.out.fire

}
