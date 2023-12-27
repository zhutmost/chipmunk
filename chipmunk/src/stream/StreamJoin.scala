package chipmunk
package stream

import chisel3._
import chisel3.util._

object StreamJoin {

  /** Join multiple StreamIOs into a single StreamIO, where its payload (bits) are unconnected. Payload needs to be
    * connected separately. The output handshake happens only once all inputs are valid.
    *
    * @param ins
    *   The input StreamIOs.
    * @return
    *   The output StreamIO driven by the input StreamIOs.
    * @example
    *   {{{
    * val in0 = Stream(UInt(32.W))
    * val in1 = Stream(UInt(32.W))
    * val out = StreamJoin.withoutPayload(Seq(in0, in1))
    *   }}}
    */
  def withoutPayload[T <: Data](ins: IndexedSeq[StreamIO[T]]): StreamIO[EmptyBundle] = {
    val uJoin = Module(new StreamJoin(ins.length))
    uJoin.io.ins.zip(ins).foreach { case (joinIn, in) => joinIn.handshakeFrom(in) }
    uJoin.io.out
  }

  /** Join multiple StreamIOs into a single StreamIO, where its payload (bits) are unconnected. Payload needs to be
    * connected separately. The output handshake happens only once all inputs are valid.
    *
    * @param in0
    *   The input StreamIOs.
    * @param ins
    *   more input StreamIOs.
    * @return
    *   The output StreamIO driven by the input StreamIOs.
    * @example
    *   {{{
    * val in0 = Stream(UInt(32.W))
    * val in1 = Stream(UInt(32.W))
    * val out = StreamJoin.withoutPayload(in0, in1)
    *   }}}
    */
  def withoutPayload[T <: Data](in0: StreamIO[T], ins: StreamIO[T]*): StreamIO[EmptyBundle] = {
    withoutPayload(in0 +: ins.toIndexedSeq)
  }

  def vecMerge[T <: Data](in0: StreamIO[T], ins: StreamIO[T]*): StreamIO[MixedVec[T]] = {
    val emptyOut = withoutPayload(in0, ins: _*)
    emptyOut.payloadReplace(MixedVecInit(in0.bits +: ins.map(_.bits)))
  }

  def vecMerge[T <: Data](ins: IndexedSeq[StreamIO[T]]): StreamIO[MixedVec[T]] = {
    val emptyOut = withoutPayload(ins)
    emptyOut.payloadReplace(MixedVecInit(ins.map(_.bits)))
  }
}

/** Joins `num` upstream StreamIOs to a single StreamIO. The output handshake happens only once all inputs are valid.
  *
  * @param num
  *   The number of StreamIOs to join.
  */
class StreamJoin[T <: Data](num: Int) extends Module {
  require(num >= 2, "StreamJoin must have at least 2 input StreamIOs.")
  val io = IO(new Bundle {
    val ins = Vec(num, Slave(Stream.empty))
    val out = Master(Stream.empty)
  })

  io.out.valid := io.ins.map(_.valid).reduce(_ & _)
  io.ins.foreach(_.ready := io.out.fire)
}
