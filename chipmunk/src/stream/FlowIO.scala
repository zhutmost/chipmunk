package chipmunk
package stream

import chisel3.*
import chisel3.experimental.{requireIsChiselType, requireIsHardware, SourceInfo}
import chisel3.experimental.BundleLiterals.*
import chisel3.util.{RegEnable, Valid}

import chipmunk.{EmptyBundle, IsMasterSlave}

/** A valid-only interface extending Chisel's [[ValidIO]].
  *
  * Each cycle with `valid` asserted represents one transaction. There is no backpressure: the receiver must accept
  * every valid transaction. Payload `bits` is meaningful only when `valid` is asserted.
  */
class FlowIO[T <: Data](gen: T) extends Valid[T](gen) with IsMasterSlave {
  override def isMaster: Boolean = true

  /** Connect from `that` and returns `that` for right-to-left chaining. */
  def <<(that: FlowIO[T])(using SourceInfo): FlowIO[T] = {
    connectFrom(that)
    that
  }

  /** Connect to `that` and return `that` for left-to-right chaining. */
  def >>(that: FlowIO[T])(using SourceInfo): FlowIO[T] = {
    that.connectFrom(this)
    that
  }

  /** Connect from `that` through [[pipeForward]], cutting the forward `valid`/`bits` paths with registers. */
  def <-<(that: FlowIO[T])(using SourceInfo): FlowIO[T] = {
    connectFrom(that.pipeForward())
    that
  }

  /** Connect to `that` through [[pipeForward]], cutting the forward `valid`/`bits` paths with registers. */
  def >->(that: FlowIO[T])(using SourceInfo): FlowIO[T] = {
    that.connectFrom(pipeForward())
    that
  }

  /** Connect `valid` from `that`, without driving `bits`. */
  infix def handshakeFrom[U <: Data](that: Valid[U])(using SourceInfo): Unit = {
    valid := that.valid
  }

  /** Connect `valid` and `bits` from `that`. */
  infix def connectFrom(that: Valid[T])(using SourceInfo): Unit = {
    handshakeFrom(that)
    bits := that.bits
  }

  /** Transform the payload `bits` using `f` without changing handshake timing.
    *
    * The returned interface is read-only.
    *
    * @param f
    *   The payload mapping function, which is evaluated once during elaboration and must return hardware.
    * @example
    *   {{{
    * val oldFlow = Wire(Flow(UInt(8.W)))
    * val newFlow = oldFlow.payloadMap(x => {
    *   val y = Wire(Bool())
    *   y := (x + 1.U) >= 3.U
    *   y
    * })
    *   }}}
    */
  def payloadMap[U <: Data](f: T => U)(using SourceInfo): FlowIO[U] = {
    val payload = f(bits)
    requireIsHardware(payload)

    val ret = Wire(Flow(chiselTypeOf(payload)))
    ret handshakeFrom this
    ret.bits := payload
    ret.readOnly
  }

  /** Apply [[payloadMap]], returning a read-only FlowIO. */
  override def map[U <: Data](f: T => U): FlowIO[U] = payloadMap(f)

  /** Replace the payload `bits` with `p` without changing handshake timing.
    *
    * The returned interface is read-only.
    *
    * @param p
    *   The new hardware payload.
    * @example
    *   {{{
    * val oldFlow = Wire(Flow(UInt(8.W)))
    * val newFlow = oldFlow.payloadReplace(12.S(8.W))
    *   }}}
    */
  def payloadReplace[U <: Data](p: U)(using SourceInfo): FlowIO[U] = {
    payloadMap(_ => p)
  }

  /** Cast the payload `bits` to another type `gen` without changing handshake timing.
    *
    * This is a bit reinterpretation, not an arithmetic conversion. To replace the payload with other hardware signals,
    * use [[payloadReplace]] or [[payloadMap]]. The returned interface is read-only.
    *
    * @param gen
    *   Unbound target payload type.
    * @param checkWidth
    *   If true, require known and equal source and target widths. If false, the payload may be padded or truncated.
    * @example
    *   {{{
    * val oldFlow = Wire(Flow(UInt(8.W)))
    * val newFlow = oldFlow.payloadCast(SInt(8.W))
    *   }}}
    */
  def payloadCast[U <: Data](gen: U, checkWidth: Boolean = false)(using SourceInfo): FlowIO[U] = {
    requireIsChiselType(gen)

    if (checkWidth) {
      require(bits.isWidthKnown && gen.isWidthKnown, "Payload width checking requires known source and target widths.")
      require(bits.getWidth == gen.getWidth, s"Payload width mismatch: ${bits.getWidth} != ${gen.getWidth}")
    }

    payloadMap(_.asTypeOf(gen))
  }

  /** Return this interface unchanged, without adding hardware. */
  def pipePassThrough(): FlowIO[T] = this

  /** Cut the forward `valid`/`bits` paths with a one-entry buffer.
    *
    * Minimum latency: one cycle. Maximum throughput: one transaction per cycle. For an N-bit payload, uses N + 1
    * register bits. Only `valid` is reset to false; `bits` is updated on valid input cycles.
    */
  def pipeForward()(using SourceInfo): FlowIO[T] = {
    val ret = Wire(Flow(chiselTypeOf(bits)))
    ret.valid := RegNext(valid, false.B)
    ret.bits  := RegEnable(bits, valid)
    ret
  }

  /** Insert `n` forward pipeline stages using [[pipeForward]].
    *
    * Adds `n` cycles of latency. If `n` is zero, return this interface unchanged.
    *
    * @param n
    *   Number of pipeline stages; must be nonnegative.
    */
  def stage(n: Int = 1)(using SourceInfo): FlowIO[T] = {
    require(n >= 0, "The number of pipeline stages must be nonnegative.")

    var ret = this
    for (_ <- 0 until n) {
      ret = ret.pipeForward()
    }
    ret
  }

  /** Drop transactions when `cond` is false. */
  def takeWhen(cond: Bool)(using SourceInfo): FlowIO[T] = {
    val ret = Wire(Flow(chiselTypeOf(bits)))
    ret.valid := valid && cond
    ret.bits  := bits
    ret
  }

  /** Drop transactions when `cond` is true. */
  def throwWhen(cond: Bool)(using SourceInfo): FlowIO[T] = {
    takeWhen(!cond)
  }

  /** Convert this Flow to a Stream by forwarding `valid` and `bits`.
    *
    * This method adds no buffering and cannot backpressure the Flow. The consumer must drive `ready` high whenever
    * `valid` is high to avoid losing transactions.
    */
  def asStream(using SourceInfo): StreamIO[T] = {
    val ret = Wire(Stream(chiselTypeOf(bits)))
    ret.valid := valid
    ret.bits  := bits
    ret
  }
}

/** Factories for FlowIO types and connected interfaces. */
object Flow {

  /** Create an unbound interface type, suitable for IO, Wire, and Reg. */
  def apply[T <: Data](gen: T): FlowIO[T] = new FlowIO(gen)

  /** Create an unbound interface type with no payload. */
  def apply(): FlowIO[EmptyBundle] = new FlowIO(new EmptyBundle)

  /** Create an unbound interface type with no payload. */
  def empty: FlowIO[EmptyBundle] = apply()

  /** Create an unbound interface type from the payload type of existing hardware. */
  def like[T <: Data](source: Valid[T]): FlowIO[T] = {
    requireIsHardware(source)
    apply(chiselTypeOf(source.bits))
  }

  /** Create and connect a FlowIO wire from existing valid-only hardware. */
  def from[T <: Data](source: Valid[T])(using SourceInfo): FlowIO[T] = {
    val ret = Wire(like(source))
    ret.connectFrom(source)
    ret
  }
}

/** Factory for writable FlowIO registers. */
object RegFlow {

  /** Create a writable FlowIO register.
    *
    * Only `valid` is reset to false; `bits` is not reset. Fields retain their values unless assigned.
    */
  def apply[T <: Data](gen: T)(using SourceInfo): FlowIO[T] = {
    RegInit(Flow(gen).Lit(_.valid -> false.B))
  }
}
