package chipmunk
package stream

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.MixedVec

/** Broadcast each transaction exactly once to every output, allowing independent output handshakes. */
object StreamFork {

  /** Fork only the handshake, leaving output payloads empty for subsequent [[StreamIO.payloadReplace]] calls. */
  def withoutPayload[T <: Data](in: StreamIO[T], num: Int)(using SourceInfo): Vec[StreamIO[EmptyBundle]] = {
    val fork = Module(new StreamFork(num))
    fork.io.in.handshakeFrom(in)
    fork.io.outs
  }

  /** Broadcast the same payload to every output. Input transfers only after every output has accepted it. */
  def duplicate[T <: Data](in: StreamIO[T], num: Int)(using SourceInfo): Vec[StreamIO[T]] = {
    val control = withoutPayload(in, num)
    val outs    = Wire(Vec(num, Stream.like(in)))
    outs.zip(control).foreach { case (out, handshake) =>
      out.handshakeFrom(handshake)
      out.bits := in.bits
    }
    outs
  }

  /** Distribute a homogeneous Vec payload, with exactly one transfer to each element's output. */
  def vecSplit[T <: Data](in: StreamIO[Vec[T]])(using SourceInfo): Vec[StreamIO[T]] = {
    val control = withoutPayload(in, in.bits.length)
    val outs    = Wire(Vec(in.bits.length, Stream(chiselTypeOf(in.bits(0)))))
    outs.zip(control).zip(in.bits).foreach { case ((out, handshake), bits) =>
      out.handshakeFrom(handshake)
      out.bits := bits
    }
    outs
  }

  /** Distribute a MixedVec payload while retaining each element's hardware type and width. */
  def vecSplit[T <: Data](in: StreamIO[MixedVec[T]])(using SourceInfo): IndexedSeq[StreamIO[T]] = {
    val control = withoutPayload(in, in.bits.length)
    in.bits.indices.map(index => control(index).payloadReplace(in.bits(index))).toIndexedSeq
  }

  /** Evaluate a MixedVec mapping once during elaboration, then distribute its elements. */
  def vecMap[T <: Data, U <: Data](in: StreamIO[T], f: T => MixedVec[U])(using SourceInfo): IndexedSeq[StreamIO[U]] =
    vecSplit(in.payloadMap(f))
}

/** Handshake-only fork. Payload is held by the input producer until all branches complete.
  *
  * Branches may transfer before the input transfers. This is a broadcast, not a simultaneous/atomic transfer to all
  * outputs. Reset abandons partially distributed transactions and must be coordinated with the connected endpoints.
  */
class StreamFork(num: Int) extends Module {
  require(num >= 1, "StreamFork requires at least one output.")

  val io = IO(new Bundle {
    val in   = Slave(Stream.empty)
    val outs = Vec(num, Master(Stream.empty))
  })

  private val done = RegInit(VecInit(Seq.fill(num)(false.B)))

  io.in.ready := io.outs.zip(done).map { case (out, accepted) => accepted || out.ready }.reduce[Bool](_ && _)
  io.outs.zipWithIndex.foreach { case (out, index) =>
    out.valid := io.in.valid && !done(index)
    out.bits  := io.in.bits
    when(out.fire) {
      done(index) := true.B
    }
  }
  when(io.in.fire) {
    done.foreach(_ := false.B)
  }
}
