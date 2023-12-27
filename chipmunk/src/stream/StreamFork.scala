package chipmunk
package stream

import chisel3.util._
import chisel3._

/** Fork a StreamIO into multiple StreamIOs. */
object StreamFork {

  /** Fork a StreamIO into multiple StreamIOs, where their payloads (bits) are unconnected. Payload needs to be
    * connected separately.
    *
    * @param in
    *   The input StreamIO.
    * @param num
    *   The number of forked downstream StreamIOs.
    * @return
    *   The output downstream StreamIOs driven by the input StreamIO.
    * @example
    *   {{{
    * val in = Stream(UInt(32.W))
    * val outs = StreamFork.withoutPayload(in, 2)
    *   }}}
    */
  def withoutPayload[T <: Data](in: StreamIO[T], num: Int): Vec[StreamIO[EmptyBundle]] = {
    val uFork = Module(new StreamFork(num))
    uFork.io.in.handshakeFrom(in)
    uFork.io.outs
  }

  /** Fork a StreamIO into multiple StreamIOs, where their payloads (bits) are the same as the input StreamIO.
    *
    * @param in
    *   The input StreamIO.
    * @param num
    *   The number of forked downstream StreamIOs.
    * @return
    *   The output downstream StreamIOs driven by the input StreamIO.
    * @example
    *   {{{
    * val in = Stream(UInt(32.W))
    * val outs = StreamFork.duplicate(in, 2)
    *   }}}
    */
  def duplicate[T <: Data](in: StreamIO[T], num: Int): Vec[StreamIO[T]] = {
    val emptyOuts = withoutPayload(in, num)
    VecInit(emptyOuts.map(_.payloadReplace(in.bits)))
  }

  /** Fork a StreamIO of `Vec` into a multiple StreamIOs, where their payloads (bits) are each element in the `Vec`
    * respectively. The number of forked downstream StreamIOs is the same as the length of the `Vec`.
    *
    * @param in
    *   The input StreamIO.
    * @return
    *   The output downstream StreamIOs driven by the input StreamIO.
    * @example
    *   {{{
    * val in = Stream(Vec(16, UInt(32.W)))
    * val outs = StreamFork.vecSplit(in)
    *   }}}
    */
  def vecSplit[T <: Data](in: StreamIO[Vec[T]]): Vec[StreamIO[T]] = {
    val emptyOuts = withoutPayload(in, in.bits.length)
    VecInit(emptyOuts zip in.bits map { case (o, p) =>
      o.payloadReplace(p)
    })
  }

  /** Fork a StreamIO of `MixedVec` into a multiple StreamIOs, where their payloads (bits) are each element in the
    * `MixedVec` respectively. The number of forked downstream StreamIOs is the same as the length of the `MixedVec`.
    *
    * @param in
    *   The input StreamIO.
    * @return
    *   The output downstream StreamIOs driven by the input StreamIO.
    * @example
    *   {{{
    * val in = Stream(MixedVec(Seq(UInt(32.W), UInt(32.W))))
    * val outs = StreamFork.vecSplit(in)
    *   }}}
    */
  def vecSplit[T <: Data](in: StreamIO[MixedVec[T]]): IndexedSeq[StreamIO[T]] = {
    val emptyOuts = withoutPayload(in, in.bits.length)
    emptyOuts zip in.bits map { case (o, p) =>
      o.payloadReplace(p)
    }
  }

  /** Fork a StreamIO of `Vec` into a multiple StreamIOs, where their payloads (bits) are each element in the `MixedVec`
    * generated from `fn(in.bits)`. The number of forked downstream StreamIOs is the same as the length of the
    * `MixedVec`.
    *
    * @param in
    *   The input StreamIO.
    * @return
    *   The output downstream StreamIOs driven by the input StreamIO.
    * @example
    *   {{{
    * val in = Stream(UInt(32.W))
    * val outs = StreamFork.vecMap(in, { x => MixedVecInit(x + 1.U, x + 2.U) })
    *   }}}
    */
  def vecMap[T <: Data, T2 <: Data](in: StreamIO[T], fn: T => MixedVec[T2]): IndexedSeq[StreamIO[T2]] = {
    vecSplit(in.payloadMap(fn))
  }
}

/** Connects the input StreamIO handshake to the all of `num` output StreamIO handshakes. For each input transaction,
  * every output stream handshakes exactly once. The input StreamIO only fires when all output StreamIOs have fired, but
  * the output streams do not have to handshake simultaneously.
  *
  * The payload is not included in the output StreamIOs. Please connect them with [[StreamIO.payloadReplace]] manually
  * outside of this module.
  *
  * @param num
  *   The number of StreamIOs to fork.
  * @return
  *   The forked StreamIOs.
  */
class StreamFork(num: Int) extends Module {
  require(num >= 2, "StreamFork must have at least 2 output Streams.")
  val io = IO(new Bundle {
    val in   = Slave(Stream.empty)
    val outs = Vec(num, Master(Stream.empty))
  })

  val linkEnable = RegInit(VecInit(Seq.fill(num)(true.B)))

  // Ready is true when every output stream takes or has taken its value
  io.in.ready := true.B
  for (i <- 0 until num) {
    when(!io.outs(i).ready && linkEnable(i)) {
      io.in.ready := false.B
    }
  }

  // Outputs are valid if the input is valid and they haven't taken their value yet.
  // When an output fires, mark its value as taken.
  for (i <- 0 until num) {
    io.outs(i).valid := io.in.valid && linkEnable(i)
    io.outs(i).bits  := io.in.bits
    when(io.outs(i).fire) {
      linkEnable(i) := false.B
    }
  }

  // Reset the storage for each new value
  when(io.in.ready) {
    linkEnable.foreach(_ := true.B)
  }
}
