package chipmunk
package stream

import chisel3.util._
import chisel3._

/** Fork a FlowIO into multiple FlowIOs. */
object FlowFork {

  /** Fork a FlowIO into multiple FlowIOs, where their payloads (bits) are unconnected. Payload needs to be connected
    * separately.
    *
    * @param in
    *   The input FlowIO.
    * @param num
    *   The number of forked downstream FlowIOs.
    * @return
    *   The output downstream FlowIOs driven by the input FlowIO.
    * @example
    *   {{{
    * val in = Flow(UInt(32.W))
    * val outs = FlowFork.withoutPayload(in, 2)
    *   }}}
    */
  def withoutPayload[T <: Data](in: FlowIO[T], num: Int): Vec[FlowIO[EmptyBundle]] = {
    require(num >= 2, "FlowFork must have at least 2 output FlowIOs.")
    val emptyOuts = Wire(Vec(num, Flow.empty))
    emptyOuts.foreach(_.handshakeFrom(in))
    emptyOuts
  }

  /** Fork a FlowIO into multiple FlowIOs, where their payloads (bits) are the same as the input FlowIO.
    *
    * @param in
    *   The input FlowIO.
    * @param num
    *   The number of forked downstream FlowIOs.
    * @return
    *   The output downstream FlowIOs driven by the input FlowIO.
    * @example
    *   {{{
    * val in = Flow(UInt(32.W))
    * val outs = FlowFork.duplicate(in, 2)
    *   }}}
    */
  def duplicate[T <: Data](in: FlowIO[T], num: Int): Vec[FlowIO[T]] = {
    val emptyOuts = withoutPayload(in, num)
    VecInit(emptyOuts.map(_.payloadReplace(in.bits)))
  }

  /** Fork a FlowIO of `Vec` into a multiple FlowIOs, where their payloads (bits) are each element in the `Vec`
    * respectively. The number of forked downstream FlowIOs is the same as the length of the `Vec`.
    *
    * @param in
    *   The input FlowIO.
    * @return
    *   The output downstream FlowIOs driven by the input FlowIO.
    * @example
    *   {{{
    * val in = Flow(Vec(16, UInt(32.W)))
    * val outs = FlowFork.vecSplit(in)
    *   }}}
    */
  def vecSplit[T <: Data](in: FlowIO[Vec[T]]): Vec[FlowIO[T]] = {
    val emptyOuts = withoutPayload(in, in.bits.length)
    VecInit(emptyOuts zip in.bits map { case (o, p) =>
      o.payloadReplace(p)
    })
  }

  /** Fork a FlowIO of `MixedVec` into a multiple FlowIOs, where their payloads (bits) are each element in the
    * `MixedVec` respectively. The number of forked downstream FlowIOs is the same as the length of the `MixedVec`.
    *
    * Because there are some bugs in the implementation of `MixedVecInit`, it returns `IndexedSeq` instead of
    * `MixedVec`.
    *
    * @param in
    *   The input FlowIO.
    * @return
    *   The output downstream FlowIOs driven by the input FlowIO.
    * @example
    *   {{{
    * val in = Flow(MixedVec(true.B, 12.U(8.W)))
    * val outs = FlowFork.vecSplit(in)
    *   }}}
    */
  def vecSplit[T <: Data](in: FlowIO[MixedVec[T]]): IndexedSeq[FlowIO[T]] = {
    val emptyOuts = withoutPayload(in, in.bits.length)
    emptyOuts zip in.bits map { case (o, p) =>
      o.payloadReplace(p)
    }
  }

  /** Fork a FlowIO of `Vec` into a multiple FlowIOs, where their payloads (bits) are each element in the `MixedVec`
    * generated from `fn(in.bits)`. The number of forked downstream FlowIOs is the same as the length of the `MixedVec`.
    *
    * @param in
    *   The input FlowIO.
    * @param fn
    *   The function to map the payload of the input FlowIO to a MixedVec, whose each element drives the payload of one
    *   output FlowIO.
    * @return
    *   The output downstream FlowIOs driven by the input FlowIO.
    * @example
    *   {{{
    * val in = Flow(UInt(32.W))
    * val outs = FlowFork.vecMap(in, { x => MixedVecInit(x + 1.U, x + 2.U) })
    *   }}}
    */
  def vecMap[T <: Data, T2 <: Data](in: FlowIO[T], fn: T => MixedVec[T2]): IndexedSeq[FlowIO[T2]] = {
    vecSplit(in.payloadMap(fn))
  }
}
