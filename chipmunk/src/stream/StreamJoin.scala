package chipmunk
package stream

import scala.collection.Seq

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{MixedVec, MixedVecInit, ReadyValidIO}

/** Join input transactions by position; all inputs transfer together with the output. */
object StreamJoin {

  /** Join handshakes with heterogeneous payloads, without buffering or checking transaction identities. */
  def withoutPayload(ins: Seq[ReadyValidIO[Data]])(using SourceInfo): StreamIO[EmptyBundle] = {
    require(ins.nonEmpty, "StreamJoin requires at least one input.")
    val join = Module(new StreamJoin(ins.length))
    join.io.ins.zip(ins).foreach { case (sink, source) => sink.handshakeFrom(source) }
    join.io.out
  }

  /** Varargs form of [[withoutPayload]]. Every producer must obey the StreamIO stability contract. */
  def withoutPayload(in: ReadyValidIO[Data], ins: ReadyValidIO[Data]*)(using SourceInfo): StreamIO[EmptyBundle] =
    withoutPayload(in +: ins)

  /** Combine two differently typed payloads using a callback evaluated once during elaboration. */
  def map[A <: Data, B <: Data, R <: Data](a: StreamIO[A], b: StreamIO[B])(f: (A, B) => R)(using
    SourceInfo
  ): StreamIO[R] = withoutPayload(a, b).payloadMap(_ => f(a.bits, b.bits))

  /** Merge homogeneous payloads into a Vec in input order. */
  def vecMerge[T <: Data](ins: Seq[StreamIO[T]])(using SourceInfo): StreamIO[Vec[T]] =
    withoutPayload(ins).payloadReplace(VecInit(ins.map(_.bits).toVector))

  /** Varargs form of [[vecMerge]]. */
  def vecMerge[T <: Data](in: StreamIO[T], ins: StreamIO[T]*)(using SourceInfo): StreamIO[Vec[T]] =
    vecMerge(in +: ins)

  /** Merge differently typed or sized payloads into a MixedVec in input order. */
  def mixedVecMerge[T <: Data](ins: Seq[ReadyValidIO[T]])(using SourceInfo): StreamIO[MixedVec[T]] =
    withoutPayload(ins).payloadReplace(MixedVecInit(ins.map(_.bits).toVector))

  /** Varargs form of [[mixedVecMerge]]. */
  def mixedVecMerge[T <: Data](in: ReadyValidIO[T], ins: ReadyValidIO[T]*)(using SourceInfo): StreamIO[MixedVec[T]] =
    mixedVecMerge(in +: ins)
}

/** Handshake-only join. All producers must hold offered transactions until transfer. */
class StreamJoin(num: Int) extends Module {
  require(num >= 1, "StreamJoin requires at least one input.")

  val io = IO(new Bundle {
    val ins = Vec(num, Slave(Stream.empty))
    val out = Master(Stream.empty)
  })

  io.out.valid := io.ins.map(_.valid).reduce[Bool](_ && _)
  io.out.bits  := DontCare
  io.ins.foreach(_.ready := io.out.fire)
}
