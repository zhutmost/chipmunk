package chipmunk
package stream

import chisel3._
import chisel3.experimental.{requireIsHardware, requireIsChiselType}
import chisel3.util._

class StreamIO[T <: Data](gen: T) extends DecoupledIO[T](gen) with IsMasterSlave {
  def isMaster = true

  /** Drive this StreamIO from that. */
  def <<(that: StreamIO[T]): StreamIO[T] = {
    connectFrom(that)
    that
  }

  /** Drive that StreamIO from this. */
  def >>(that: StreamIO[T]): StreamIO[T] = {
    that << this
    that
  }

  /** Drive this StreamIO from that, where the forward valid/bits paths are cut by registers (see [[pipeForward]]). */
  def <-<(that: StreamIO[T]): StreamIO[T] = {
    this << that.pipeForward()
    that
  }

  /** Drive that StreamIO from this, where the forward valid/bits paths are cut by registers (see [[pipeForward]]). */
  def >->(that: StreamIO[T]): StreamIO[T] = {
    that <-< this
    that
  }

  /** Drive this StreamIO from that, where the backward ready path is cut by registers (see [[pipeBackward]]). */
  def <|<(that: StreamIO[T]): StreamIO[T] = {
    this << that.pipeBackward()
    that
  }

  /** Drive that StreamIO from this, where the backward ready path is cut by registers (see [[pipeBackward]]). */
  def >|>(that: StreamIO[T]): StreamIO[T] = {
    that <|< this
    that
  }

  /** Drive this StreamIO from that, where the valid/ready/bits paths are cut by registers. */
  def <+<(that: StreamIO[T]): StreamIO[T] = {
    this << that.pipeAll()
    that
  }

  /** Drive that StreamIO from this, where the valid/ready/bits paths are cut by registers. */
  def >+>(that: StreamIO[T]): StreamIO[T] = {
    that <+< this
    that
  }

  /** Drive the handshake signals (valid & ready) of this StreamIO from that. */
  def handshakeFrom[T2 <: Data](that: StreamIO[T2]): Unit = {
    this.valid := that.valid
    that.ready := this.ready
  }

  /** Drive the all signals of this StreamIO from that. */
  def connectFrom(that: StreamIO[T]): Unit = {
    handshakeFrom(that)
    this.bits := that.bits
  }

  /** Map the payload (bits) to something else.
    *
    * @param f
    *   The payload mapping function.
    * @return
    *   The new StreamIO whose payload is converted from this.
    * @example
    *   {{{
    * val oldStream = Stream(UInt(8.W))
    * val newStream = oldStream.payloadMap(x => {
    *   val y = Wire(Bool())
    *   y := (x + 1.U) >= 3.U
    *   y
    * })
    *   }}}
    */
  def payloadMap[T2 <: Data](f: T => T2): StreamIO[T2] = {
    val payload = f(bits)
    val ret     = Wire(Stream(chiselTypeOf(payload)))
    ret.handshakeFrom(this)
    ret.bits := payload
    ret
  }

  /** Replace this stream's payload (bits) with something else. If you want to cast the payload to another type, use
    * [[payloadCast]].
    *
    * @param p
    *   The new payload.
    * @example
    *   {{{
    * val oldStream = Stream(UInt(8.W))
    * val newStream = oldStream.payloadReplace(12.S(8.W))
    *   }}}
    */
  def payloadReplace[T2 <: Data](p: T2): StreamIO[T2] = {
    requireIsHardware(p)
    payloadMap(_ => p)
  }

  /** Cast this StreamIO's payload (bits) to another type. If you want to replace the payload with other hardware
    * signals, use [[payloadReplace]].
    *
    * @param gen
    *   Target chisel type.
    * @param checkWidth
    *   Whether to check the bit width of the payload is the same as the target type. If false, the payload may be
    *   padded or truncated. If true, throw an exception when the bit width is not the same.
    * @return
    *   The new StreamIO whose payload is converted from this.
    * @example
    *   {{{
    * val oldStream = Stream(UInt(8.W))
    * val newStream = oldStream.payloadCast(SInt(8.W))
    *   }}}
    */
  def payloadCast[T2 <: Data](gen: T2, checkWidth: Boolean = false): StreamIO[T2] = {
    requireIsChiselType(gen)
    if (checkWidth) {
      require(gen.getWidth == bits.getWidth, s"Payload width mismatch: ${gen.getWidth} != ${bits.getWidth}")
    }
    payloadMap(_.asTypeOf(gen))
  }

  /** True if data are present but the ready is still low (i.e. valid && !ready). */
  def isPending: Bool = valid && !ready

  /** True if the stream is ready to receive data but no data is present (i.e. !valid && ready). */
  def isStarving: Bool = !valid && ready

  /** Do nothing, but it is nice to separate signals for combinatorial transformations. */
  def pipePassThrough(): StreamIO[T] = this

  /** Cut the valid & payload (bits) path by registers for better timing.
    *
    * It does not affect throughput, but introduces extra latency. Its area overhead is mainly (N + 1) registers and a
    * little combinatorial logic.
    *
    * @param bubbleCollapse
    *   Collapse bubbles in the forward path with tiny combinatorial logic.
    * @return
    *   StreamIO with registers inserted.
    */
  def pipeForward(bubbleCollapse: Boolean = true): StreamIO[T] = {
    val ret    = Wire(Stream(gen))
    val rValid = RegEnable(valid, false.B, ret.ready)
    val rBits  = RegEnable(bits, this.fire)

    if (bubbleCollapse) {
      ready := ret.ready || !ret.valid
    } else {
      ready := ret.ready
    }

    ret.valid := rValid
    ret.bits  := rBits
    ret
  }

  /** Cut the ready path by registers for better timing.
    *
    * It does not affect throughput or latency. Its area overhead is mainly (N + 1) registers and N 2-to-1 multiplexers,
    * where N is the bit width of payload.
    *
    * @return
    *   StreamIO with registers inserted.
    */
  def pipeBackward(): StreamIO[T] = {
    val ret    = Wire(Stream(gen))
    val rValid = RegInit(false.B)
    val rBits  = RegEnable(bits, this.fire && !ret.ready)

    when(ret.ready) {
      rValid := false.B
    }.elsewhen(valid) {
      rValid := true.B
    }

    ready := !rValid

    // If rBits/rValid is nonempty, the data in them are taken in first.
    ret.valid := valid || rValid
    ret.bits  := Mux(rValid, rBits, bits)
    ret
  }

  /** Cut the valid & ready & payload (bits) path by registers for better timing.
    *
    * Alias of this.pipeForward().pipeBackward(). It does not affect throughput, but introduces extra latency. Its area
    * overhead is mainly (2N + 2) registers and a little combinatorial logic, where N is the bit width of payload.
    *
    * @return
    *   StreamIO with registers inserted.
    */
  def pipeAll(): StreamIO[T] = pipeForward().pipeBackward()

  /** Cut the ready, valid & payload (bits) path by registers for better timing.
    *
    * It does a handshake every other cycle at most, losing half of the bandwidth. Its area overhead is mainly (N + 2)
    * registers, where N is the bit width of payload.
    *
    * If the bandwidth loss is unacceptable, consider using [[pipeAll]] instead.
    *
    * @return
    *   StreamIO with registers inserted.
    */
  def pipeSimple(): StreamIO[T] = {
    val ret    = Wire(Stream(gen))
    val rValid = RegInit(false.B)
    val rBits  = RegEnable(bits, this.fire)

    when(ret.fire) {
      rValid := false.B
    }.elsewhen(valid) {
      rValid := true.B
    }

    ready := !rValid

    ret.valid := rValid
    ret.bits  := rBits
    ret
  }

  /** Cut the valid path by registers for better timing.
    *
    * Its area overhead is mainly 1 register.
    *
    * @return
    *   StreamIO with registers inserted.
    */
  def pipeValid(): StreamIO[T] = {
    val ret    = Wire(Stream(gen))
    val rValid = RegInit(false.B)

    when(ret.fire) {
      rValid := false.B
    }.elsewhen(valid) {
      rValid := true.B
    }

    ready := ret.fire

    ret.valid := rValid
    ret.bits  := bits
    ret
  }

  /** Alias of [[pipeForward]]. */
  def stage(): StreamIO[T] = pipeForward()

  /** Block this stream when `cond` is False. */
  def continueWhen(cond: => Bool): StreamIO[T] = {
    val ret = Wire(Stream(gen))
    ret.valid  := this.valid && cond
    this.ready := ret.ready && cond
    ret.bits   := this.bits
    ret
  }

  /** Block this stream when `cond` is True. */
  def haltWhen(cond: => Bool): StreamIO[T] = continueWhen(!cond)

  /** Throw transactions when `cond` is True. */
  def throwWhen(cond: => Bool): StreamIO[T] = {
    val ret = Stream(gen)
    ret << this
    when(cond) {
      ret.valid  := false.B
      this.ready := true.B
    }
    ret
  }

  /** Throw transactions when `cond` is False. */
  def takeWhen(cond: => Bool): StreamIO[T] = throwWhen(!cond)

  /** Wrap valid & payload (bits) signals of this StreamIO as a FlowIO, and the ready signal is ignored (unconnected and
    * unused).
    *
    * This is a dangerous conversion! FlowIO's firing only relies on the valid signal of this StreamIO, regardless of
    * the ready signal is high or low. If you want the transaction of FlowIO depends on `valid && ready` of this
    * StreamIO, consider using [[toFlow]].
    *
    * @return
    *   The result FlowIO.
    */
  def asFlow: FlowIO[T] = {
    val ret = Wire(Flow(gen))
    ret.valid := valid
    ret.bits  := bits
    ret
  }

  /** Convert this StreamIO to FlowIO.
    *
    * The FlowIO fires only when this StreamIO fires. That is to say, the valid signal of the result FlowIO is driven
    * from `valid && ready` of this StreamIO.
    *
    * @param readyFreeRun
    *   Whether to set the ready signal of this StreamIO always high by default. If not, the ready signal is kept
    *   unconnected.
    * @return
    *   The result FlowIO.
    */
  def toFlow(readyFreeRun: Boolean = false): FlowIO[T] = {
    val ret = Wire(Flow(gen))
    ret.valid := this.fire
    ret.bits  := bits
    if (readyFreeRun) {
      ready := true.B
    }
    ret
  }

  /** Push this StreamIO to [[Queue]] and return its pop StreamIO.
    *
    * @param queueSize
    *   The Queue depth.
    * @param queueFlow
    *   Whether the inputs can be consumed on the same cycle (the inputs "flow" through the queue immediately). The
    *   `valid` signals are coupled.
    * @param queuePipe
    *   Whether a single entry queue can run at full throughput (like a pipeline). The `ready` signals are
    *   combinatorial-ly coupled.
    * @return
    *   The pop StreamIO of the queue.
    */
  def queue(queueSize: Int, queueFlow: Boolean, queuePipe: Boolean): StreamIO[T] = {
    val q = Queue(this, entries = queueSize, pipe = queueFlow, flow = queuePipe, useSyncReadMem = true)
    q.toStream
  }
}

/** StreamIO factory. */
object Stream {

  def apply[T <: Data](gen: T) = new StreamIO(gen)

  /** Returns a [[StreamIO]] interface with no payload. */
  def apply(): StreamIO[Data] = new StreamIO(new EmptyBundle)

  /** Returns a [[StreamIO]] interface with no payload. */
  def empty: StreamIO[EmptyBundle] = new StreamIO(new EmptyBundle)
}
