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

  object State extends ChiselEnum {
    val sIdle, sCount, sPend = Value
  }
  val stateCurr = RegInit(State.sIdle)
  val stateNext = WireDefault(stateCurr)
  stateCurr := stateNext

  val counterInc: Bool      = stateCurr === State.sCount
  val counterReset: Bool    = stateCurr === State.sIdle || stateNext === State.sPend
  val counter: (UInt, Bool) = Counter(1 until (1 << delayWidth), enable = counterInc, reset = counterReset)
  val counterOverflow: Bool = counter._1 === io.targetDelay - 1.U

  io.targetDelayUpdate := stateCurr === State.sCount && counterOverflow

  io.out.valid := stateCurr === State.sPend || (stateCurr === State.sIdle && io.targetDelay === 0.U && io.in.valid)
  io.in.ready  := io.out.fire

  when(stateCurr === State.sIdle) {
    when(io.in.valid) {
      stateNext := State.sCount
      when(io.targetDelay === 0.U) {
        stateNext := Mux(io.out.ready, State.sIdle, State.sPend)
      }.elsewhen(io.targetDelay === 1.U) {
        stateNext := State.sPend
      }
    }
  }.elsewhen(stateCurr === State.sCount) {
    when(counterOverflow) {
      stateNext := State.sPend
    }
  }.elsewhen(stateCurr === State.sPend) {
    when(io.out.ready) {
      stateNext := State.sIdle
    }
  }
}
