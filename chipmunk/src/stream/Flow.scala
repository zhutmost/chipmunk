package chipmunk
package stream

import chisel3._
import chisel3.experimental.{requireIsChiselType, requireIsHardware}
import chisel3.util._

class FlowIO[T <: Data](gen: T) extends Valid[T](gen) with IsMasterSlave {
  override def isMaster = true

  /** Drive this FlowIO from that. */
  def <<(that: FlowIO[T]): FlowIO[T] = connectFrom(that)

  /** Drive that FlowIO from this. */
  def >>(that: FlowIO[T]): FlowIO[T] = {
    that << this
    that
  }

  /** Drive this FlowIO from that, where the forward valid/bits paths are cut by registers (see [[pipeForward]]). */
  def <-<(that: FlowIO[T]): FlowIO[T] = {
    this << that.pipeForward()
    that
  }

  /** Drive that FlowIO from this, where the forward valid/bits paths are cut by registers (see [[pipeForward]]). */
  def >->(that: FlowIO[T]): FlowIO[T] = {
    that <-< this
  }

  /** Drive the handshake signal (valid) of this FlowIO from that. */
  def handshakeFrom[T2 <: Data](that: FlowIO[T2]): Unit = {
    this.valid := that.valid
  }

  /** Drive the all signals of this FlowIO from that. */
  def connectFrom(that: FlowIO[T]): FlowIO[T] = {
    handshakeFrom(that)
    this.bits := that.bits
    that
  }

  /** Map the payload (bits) to something else.
    *
    * @param f
    *   The payload mapping function.
    * @return
    *   The new FlowIO whose payload is converted from this.
    * @example
    *   {{{
    * val newFlow = oldFlow.payloadMap(x => {
    *   val y = Wire(Bool())
    *   y := (x + 1.U).asBool
    *   y
    * })
    *   }}}
    */
  def payloadMap[T2 <: Data](f: T => T2): FlowIO[T2] = {
    val payload = f(bits)
    val ret     = Wire(Flow(chiselTypeOf(payload)))
    ret.handshakeFrom(this)
    ret.bits := payload
    ret
  }

  /** Replace this FlowIO's payload (bits) with something else.
    *
    * If you want to cast the payload to another type, consider using [[payloadCast]].
    *
    * @param p
    *   The new payload.
    * @example
    *   {{{
    * val oldFlow = Flow(UInt(8.W))
    * val newFlow = oldFlow.payloadReplace(12.S(8.W))
    *   }}}
    */
  def payloadReplace[T2 <: Data](p: T2): FlowIO[T2] = {
    requireIsHardware(p)
    payloadMap(_ => p)
  }

  /** Cast this FlowIO's payload (bits) to another type.
    *
    * If you want to replace the payload with other hardware signals, use [[payloadReplace]].
    *
    * @param gen
    *   Target chisel type.
    * @param checkWidth
    *   Whether to check the bit width of the payload is the same as the target type. If false, the payload may be
    *   padded or truncated. If true, throw an exception when the bit width is not the same.
    * @return
    *   The new FlowIO whose payload is converted from this.
    * @example
    *   {{{
    * val oldFlow = Flow(UInt(8.W))
    * val newFlow = oldStream.payloadCast(SInt(8.W))
    *   }}}
    */
  def payloadCast[T2 <: Data](gen: T2, checkWidth: Boolean = false): FlowIO[T2] = {
    requireIsChiselType(gen)
    if (checkWidth) {
      require(gen.getWidth == bits.getWidth, s"Payload width mismatch: ${gen.getWidth} != ${bits.getWidth}")
    }
    payloadMap(_.asTypeOf(gen))
  }

  /** Do nothing, but it is nice to separate signals for combinatorial transformations. */
  def pipePassThrough(): FlowIO[T] = this

  /** Cut the valid & payload (bits) path by registers for better timing.
    *
    * It does not affect throughput, but introduces extra latency. Its area overhead is mainly (N + 1) registers and a
    * little combinatorial logic.
    *
    * @return
    *   FlowIO with registers inserted.
    */
  def pipeForward(): FlowIO[T] = {
    val ret = Wire(Flow(gen))
    ret.valid := RegNext(valid, false.B)
    ret.bits  := RegEnable(bits, valid)
    ret
  }

  /** Alias of [[pipeForward]]. */
  def stage(): FlowIO[T] = pipeForward()

  /** Throw transactions when `cond` is False. */
  def takeWhen(cond: Bool): FlowIO[T] = {
    val ret = Wire(Flow(gen))
    ret.valid := this.fire && cond
    ret.bits  := bits
    ret
  }

  /** Throw transactions when `cond` is True. */
  def throwWhen(cond: Bool): FlowIO[T] = {
    this takeWhen (!cond)
  }

  /** Create a StreamIO whose valid & payload (bits) signals are driven from this FlowIO directly.
    *
    * This is a very dangerous conversion! Because FlowIO cannot be back-pressured by the ready signal, the result
    * StreamIO may drop data when its ready signal is low. The ready signal should be always high, or it needs to be
    * handled very carefully.
    *
    * To avoid overflow, consider using [[StreamIO.queue]] together.
    *
    * @return
    *   The result StreamIO.
    */
  def asStream: StreamIO[T] = {
    val ret = Wire(stream.Stream(gen))
    ret.valid := valid
    ret.bits  := bits
    ret
  }

//  def toStream(queueSize: Int = 0, overflowThreshold: Int = 0): (StreamIO[T], Bool) = {
//    val ret      = Stream(gen)
//    val overflow = WireInit(false.B)
//    ret.valid := valid
//    ret.bits  := bits
//    overflow  := ret.isPending
//    (ret, overflow)
//  }
}

/** FlowIO factory. */
object Flow {
  def apply[T <: Data](gen: ValidIO[T]): FlowIO[T] = {
    val ret = Wire(Flow(chiselTypeOf(gen.bits)))
    ret
  }

  def apply[T <: Data](gen: T) = new FlowIO(gen)

  /** Returns a [[FlowIO]] interface with no payload. */
  def apply(): FlowIO[Data] = new FlowIO(new EmptyBundle)

  /** Returns a [[FlowIO]] interface with no payload. */
  def empty: FlowIO[EmptyBundle] = new FlowIO(new EmptyBundle)
}

object RegFlow {

  /** Create registers whose type is `FlowIO(gen)`. */
  def apply[T <: Data](gen: T): FlowIO[T] = {
    val ret = Wire(Flow(gen))
    ret.valid := RegInit(false.B)
    ret.bits  := Reg(gen)
    ret
  }
}
