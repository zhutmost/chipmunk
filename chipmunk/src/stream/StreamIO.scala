package chipmunk
package stream

import chisel3.*
import chisel3.experimental.{requireIsChiselType, requireIsHardware, SourceInfo}
import chisel3.util.{DecoupledIO, Queue, ReadyValidIO, RegEnable}

import chipmunk.{EmptyBundle, IsMasterSlave}

/** A ready/valid interface extending Chisel's [[DecoupledIO]].
  *
  * A transaction transfers when `valid && ready`. Producers must keep offered `valid`/`bits` stable until transfer.
  * Forward `valid` must not depend combinationally on backward `ready`. Payload `bits` is meaningful only when `valid`
  * is asserted.
  */
class StreamIO[T <: Data](gen: T) extends DecoupledIO[T](gen) with IsMasterSlave {
  override def isMaster: Boolean = true

  /** Connect from `that` and return `that` for right-to-left chaining. */
  def <<(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    connectFrom(that)
    that
  }

  /** Connect to `that` and return `that` for left-to-right chaining. */
  def >>(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    that.connectFrom(this)
    that
  }

  /** Connect from `that` through [[pipeForward]], where the forward `valid`/`bits` paths are cut by registers. */
  def <-<(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    connectFrom(that.pipeForward())
    that
  }

  /** Connect to `that` through [[pipeForward]], where the forward `valid`/`bits` paths are cut by registers. */
  def >->(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    that connectFrom pipeForward()
    that
  }

  /** Connect from `that` through [[pipeBackward]], where the backward `ready` path is cut by registers. */
  def <|<(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    connectFrom(that.pipeBackward())
    that
  }

  /** Connect to `that` through [[pipeBackward]], where the backward `ready` path is cut by registers. */
  def >|>(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    that connectFrom pipeBackward()
    that
  }

  /** Connect from `that` through [[pipeAll]], where the `valid`/`ready`/`bits` paths are cut by registers. */
  def <+<(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    connectFrom(that.pipeAll())
    that
  }

  /** Connect to `that` through [[pipeAll]], where the `valid`/`ready`/`bits` paths are cut by registers. */
  def >+>(that: StreamIO[T])(using SourceInfo): StreamIO[T] = {
    that connectFrom pipeAll()
    that
  }

  /** Connect `valid` from `that` and `ready` to `that`, leaving the payload `bits` unconnected. */
  infix def handshakeFrom[U <: Data](that: ReadyValidIO[U])(using SourceInfo): Unit = {
    valid      := that.valid
    that.ready := ready
  }

  /** Connect `valid`/`bits` from `that` and `ready` to `that`. */
  infix def connectFrom(that: ReadyValidIO[T])(using SourceInfo): Unit = {
    handshakeFrom(that)
    bits := that.bits
  }

  /** Transform the payload `bits` to something else through function `f` without changing handshake timing.
    *
    * @param f
    *   The payload mapping function, which is evaluated once during elaboration and must return hardware. Its result
    *   must remain stable while the output is stalled.
    * @example
    *   {{{
    * val oldStream = Wire(Stream(UInt(8.W)))
    * val newStream = oldStream.payloadMap(x => {
    *   val y = Wire(Bool())
    *   y := (x + 1.U) >= 3.U
    *   y
    * })
    *   }}}
    */
  def payloadMap[U <: Data](f: T => U)(using SourceInfo): StreamIO[U] = {
    val payload = f(bits)
    requireIsHardware(payload)

    val ret = Wire(Stream(chiselTypeOf(payload)))
    ret handshakeFrom this
    ret.bits := payload
    ret
  }

  /** Apply [[payloadMap]], preserving StreamIO as the inherited `map` return type. */
  override def map[U <: Data](f: T => U): StreamIO[U] = payloadMap(f)

  /** Replace the payload `bits` with `p` without changing handshake timing.
    *
    * @param p
    *   The new hardware payload that remains stable while the output is stalled.
    * @example
    *   {{{
    * val oldStream = Wire(Stream(UInt(8.W)))
    * val newStream = oldStream.payloadReplace(12.S(8.W))
    *   }}}
    */
  def payloadReplace[U <: Data](p: U)(using SourceInfo): StreamIO[U] = {
    payloadMap(_ => p)
  }

  /** Cast the payload `bits` to another type `gen` without changing handshake timing.
    *
    * Note that this is not an arithmetic conversion. If you want to replace the payload with other hardware signals,
    * use [[payloadReplace]] or [[payloadMap]].
    *
    * @param gen
    *   Unbound target payload type.
    * @param checkWidth
    *   Require known, equal source and target widths. If false, Chisel may pad or truncate bits.
    * @example
    *   {{{
    * val oldStream = Wire(Stream(UInt(8.W)))
    * val newStream = oldStream.payloadCast(SInt(8.W))
    *   }}}
    */
  def payloadCast[U <: Data](gen: U, checkWidth: Boolean = false)(using SourceInfo): StreamIO[U] = {
    requireIsChiselType(gen)

    if (checkWidth) {
      require(bits.isWidthKnown && gen.isWidthKnown, "Payload width checking requires known source and target widths.")
      require(bits.getWidth == gen.getWidth, s"Payload width mismatch: ${bits.getWidth} != ${gen.getWidth}")
    }

    payloadMap(_.asTypeOf(gen))
  }

  /** True if a transaction is offered but the ready is still low (i.e. `valid && !ready`). */
  def isPending(using SourceInfo): Bool = valid && !ready

  /** True if the consumer is ready but no transaction is offered (i.e. `!valid && ready`). */
  def isStarving(using SourceInfo): Bool = !valid && ready

  /** Return this interface unchanged, without adding hardware. */
  def pipePassThrough(): StreamIO[T] = this

  /** Cut the forward `valid`/`bits` paths with a one-entry buffer.
    *
    * Minimum latency: one cycle. Maximum throughput: one transaction per cycle. For an N-bit payload, uses N + 1
    * register bits.
    *
    * @param bubbleCollapse
    *   If true, an empty buffer can accept input even when output `ready` is low. If false, input `ready` follows
    *   output `ready`, so the consumer must assert `ready` without waiting for output `valid`.
    */
  def pipeForward(bubbleCollapse: Boolean = true)(using SourceInfo): StreamIO[T] = {
    val ret       = Wire(Stream(chiselTypeOf(bits)))
    val rValid    = RegInit(false.B)
    val canAccept = if (bubbleCollapse) ret.ready || !rValid else ret.ready

    ready := canAccept
    when(canAccept) {
      rValid := valid
    }

    ret.valid := rValid
    ret.bits  := RegEnable(bits, this.fire)
    ret
  }

  /** Cut the backward `ready` path with a one-entry buffer.
    *
    * Minimum latency: zero cycles. Maximum throughput: one transaction per cycle. When a buffered transaction
    * transfers, the input remains stalled for that cycle. For an N-bit payload, uses N + 1 register bits and an N-bit
    * 2-to-1 multiplexer.
    */
  def pipeBackward()(using SourceInfo): StreamIO[T] = {
    val ret    = Wire(Stream(chiselTypeOf(bits)))
    val rValid = RegInit(false.B)
    val rBits  = RegEnable(bits, this.fire && !ret.ready)

    ready := !rValid
    when(ret.ready) {
      rValid := false.B
    }.elsewhen(this.fire) {
      rValid := true.B
    }

    ret.valid := rValid || valid
    ret.bits  := Mux(rValid, rBits, bits)
    ret
  }

  /** Cut the forward `valid`/`bits` and backward `ready` paths with a one-entry buffer. Equivalent to
    * `pipeForward().pipeBackward()`.
    *
    * Minimum latency: one cycle. Maximum throughput: one transaction per cycle. For an N-bit payload, uses 2N + 2
    * register bits and an N-bit 2-to-1 multiplexer.
    */
  def pipeAll()(using SourceInfo): StreamIO[T] = pipeForward().pipeBackward()

  /** Cut the both forward `valid`/`bits` and backward `ready` paths with a one-entry buffer that cannot refill while
    * draining.
    *
    * Minimum latency: one cycle. Maximum throughput: one transaction every two cycles. The buffer cannot accept input
    * in the cycle its stored transaction transfers. For an N-bit payload, uses N + 1 register bits.
    *
    * If the bandwidth loss is unacceptable, consider using [[pipeAll]] instead.
    */
  def pipeSimple()(using SourceInfo): StreamIO[T] = {
    val ret    = Wire(Stream(chiselTypeOf(bits)))
    val rValid = RegInit(false.B)

    ready := !rValid
    when(ret.fire) {
      rValid := false.B
    }.elsewhen(this.fire) {
      rValid := true.B
    }

    ret.valid := rValid
    ret.bits  := RegEnable(bits, this.fire)
    ret
  }

  /** Cut the `valid` path with a register.
    *
    * Output `valid` becomes high one cycle after input `valid` is first observed. Input and output transfer in the same
    * cycle. Maximum throughput: one transaction every two cycles. Uses one register bit and does not store the payload.
    */
  def pipeValid()(using SourceInfo): StreamIO[T] = {
    val ret    = Wire(Stream(chiselTypeOf(bits)))
    val rValid = RegInit(false.B)

    when(ret.fire) {
      rValid := false.B
    }.elsewhen(valid) {
      rValid := true.B
    }

    ready     := ret.fire
    ret.valid := rValid
    ret.bits  := bits
    ret
  }

  /** Insert `n` forward stages; zero returns this interface unchanged. */
  def stage(n: Int = 1)(using SourceInfo): StreamIO[T] = {
    require(n >= 0, "The number of pipeline stages must be nonnegative.")

    var ret = this
    for (_ <- 0 until n) {
      ret = ret.pipeForward()
    }
    ret
  }

  /** Block transactions when `cond` is False.
    *
    * `cond` must remain unchanged while output `valid` is high and output `ready` is low.
    */
  def continueWhen(cond: => Bool)(using SourceInfo): StreamIO[T] = {
    val allow = cond
    val ret   = Wire(Stream(chiselTypeOf(bits)))
    ret.valid := valid && allow
    ready     := ret.ready && allow
    ret.bits  := bits
    ret
  }

  /** Block transactions when `cond` is True.
    *
    * `cond` must remain unchanged while output `valid` is high and output `ready` is low.
    */
  def haltWhen(cond: => Bool)(using SourceInfo): StreamIO[T] = continueWhen(!cond)

  /** Drop transactions when `cond` is True.
    *
    * `cond` must remain unchanged while output `valid` is high and output `ready` is low.
    */
  def throwWhen(cond: => Bool)(using SourceInfo): StreamIO[T] = {
    val drop = cond
    val ret  = Wire(Stream(chiselTypeOf(bits)))
    ret.valid := valid && !drop
    ready     := ret.ready || drop
    ret.bits  := bits
    ret
  }

  /** Drop transactions when `cond` is False.
    *
    * `cond` must remain unchanged while output `valid` is high and output `ready` is low.
    */
  def takeWhen(cond: => Bool)(using SourceInfo): StreamIO[T] = throwWhen(!cond)

  /** Convert this Stream to a Flow by forwarding `valid` and `bits`.
    *
    * This method does not drive `ready`. A stalled Stream transaction remains valid on the Flow and may be consumed
    * repeatedly. Use [[toFlow]] to forward only completed Stream transfers.
    *
    * The returned Flow is read-only.
    */
  def asFlow(using SourceInfo): FlowIO[T] = {
    val ret = Wire(Flow(chiselTypeOf(bits)))
    ret.valid := valid
    ret.bits  := bits
    ret.readOnly
  }

  /** Convert this Stream to a Flow by forwarding `fire` as `valid` and forwarding `bits`.
    *
    * The Flow is valid only when the Stream transfers. The returned Flow is read-only.
    *
    * @param readyFreeRun
    *   If true, drive this Stream's `ready` high. If false, leave its driver unchanged.
    */
  def toFlow(readyFreeRun: Boolean = false)(using SourceInfo): FlowIO[T] = {
    if (readyFreeRun) {
      ready := true.B
    }

    val ret = Wire(Flow(chiselTypeOf(bits)))
    ret.valid := this.fire
    ret.bits  := bits
    ret.readOnly
  }

  /** Buffer transactions in a Chisel [[Queue]]; zero depth returns this interface unchanged.
    *
    * @param queueSize
    *   Number of storage entries, i.e., the queue depth.
    * @param queueFlow
    *   Whether the inputs can be consumed on the same cycle (the inputs "flow" through the queue immediately). The
    *   `valid` signals are coupled.
    * @param queuePipe
    *   Whether a single entry queue can run at full throughput (like a pipeline). The `ready` signals are
    *   combinatorial-ly coupled.
    * @param useSyncReadMem
    *   use SyncReadMem for payload storage
    * @param flush
    *   clear buffered transactions on the active clock edge; requires positive depth
    */
  def queue(
    queueSize: Int,
    queueFlow: Boolean = false,
    queuePipe: Boolean = false,
    useSyncReadMem: Boolean = false,
    flush: Option[Bool] = None,
  )(using SourceInfo): StreamIO[T] = {
    require(queueSize >= 0, "Queue depth must be nonnegative.")
    require(queueSize > 0 || flush.isEmpty, "A zero-depth queue cannot be flushed.")

    if (queueSize == 0) {
      this
    } else {
      Stream.from(
        Queue(
          this,
          entries = queueSize,
          pipe = queuePipe,
          flow = queueFlow,
          useSyncReadMem = useSyncReadMem,
          flush = flush,
        )
      )
    }
  }

  /** Delay each transaction by `cycles` clock cycles.
    *
    * The delay starts when input `valid` is first observed. Input and output transfer in the same cycle; backpressure
    * may delay the transfer further. The payload is not buffered.
    *
    * If `cycles` is zero, return this interface unchanged.
    *
    * @param cycles
    *   Delay in clock cycles; must be nonnegative.
    */

  def delayFixed(cycles: Int)(using SourceInfo): StreamIO[T] = StreamDelay.fixed(this, cycles)

  /** Delay each transaction by a pseudorandom number of clock cycles.
    *
    * A delay between `minCycles` and `maxCycles`, inclusive, is selected when input `valid` is first observed. The
    * distribution is not guaranteed to be uniform. Input and output transfer in the same cycle; backpressure may delay
    * the transfer further. The payload is not buffered.
    *
    * If the bounds are equal, this is an equivalent to `delayFixed(maxCycles)`.
    *
    * @param maxCycles
    *   Maximum delay in clock cycles; must be greater than or equal to `minCycles`.
    * @param minCycles
    *   Minimum delay in clock cycles; must be nonnegative.
    */
  def delayRandom(maxCycles: Int, minCycles: Int = 0)(using SourceInfo): StreamIO[T] = {
    StreamDelay.random(this, maxCycles, minCycles)
  }
}

/** StreamIO factory. */
object Stream {

  /** Create an unbound interface type, suitable for IO and Wire. */
  def apply[T <: Data](gen: T): StreamIO[T] = new StreamIO(gen)

  /** Create an unbound interface type with no payload. */
  def apply(): StreamIO[EmptyBundle] = new StreamIO(new EmptyBundle)

  /** Create an unbound interface type with no payload. */
  def empty: StreamIO[EmptyBundle] = apply()

  /** Create an unbound interface type from the payload type of existing hardware. */
  def like[T <: Data](source: ReadyValidIO[T]): StreamIO[T] = {
    requireIsHardware(source)
    apply(chiselTypeOf(source.bits))
  }

  /** Wrap and connect existing ready/valid hardware without buffering.
    *
    * The source must already satisfy the StreamIO protocol; this adapter does not add stability guarantees. The
    * consumer drives the returned `ready`.
    */
  def from[T <: Data](source: ReadyValidIO[T])(using SourceInfo): StreamIO[T] = {
    val ret = Wire(like(source))
    ret connectFrom source
    ret
  }
}

extension [T <: Data](source: ReadyValidIO[T]) {

  /** Wrap and connect this interface as a StreamIO; equivalent to [[Stream.from]]. */
  def toStream(using SourceInfo): StreamIO[T] = Stream.from(source)
}
