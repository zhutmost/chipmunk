package chipmunk
package stream

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.MixedVec

/** Combinational fan-out of an unbackpressured Flow. Every output must accept every valid cycle. */
object FlowFork {

  /** Broadcast validity, leaving empty payloads for subsequent [[FlowIO.payloadReplace]] calls. */
  def withoutPayload[T <: Data](in: FlowIO[T], num: Int)(using SourceInfo): Vec[FlowIO[EmptyBundle]] = {
    require(num >= 1, "FlowFork requires at least one output.")
    val outs = Wire(Vec(num, Flow.empty))
    outs.foreach(_.handshakeFrom(in))
    outs
  }

  /** Broadcast validity and payload without adding storage or latency. */
  def duplicate[T <: Data](in: FlowIO[T], num: Int)(using SourceInfo): Vec[FlowIO[T]] = {
    require(num >= 1, "FlowFork requires at least one output.")
    val outs = Wire(Vec(num, Flow.like(in)))
    outs.foreach(_.connectFrom(in))
    outs
  }

  /** Split a homogeneous Vec payload into unbackpressured element Flows. */
  def vecSplit[T <: Data](in: FlowIO[Vec[T]])(using SourceInfo): Vec[FlowIO[T]] = {
    require(in.bits.length >= 1, "FlowFork requires at least one output.")
    val outs = Wire(Vec(in.bits.length, Flow(chiselTypeOf(in.bits(0)))))
    outs.zip(in.bits).foreach { case (out, bits) =>
      out.handshakeFrom(in)
      out.bits := bits
    }
    outs
  }

  /** Split a MixedVec payload, preserving each element's hardware type and width. */
  def vecSplit[T <: Data](in: FlowIO[MixedVec[T]])(using SourceInfo): IndexedSeq[FlowIO[T]] = {
    require(in.bits.length >= 1, "FlowFork requires at least one output.")
    in.bits.indices.map(index => in.payloadReplace(in.bits(index))).toIndexedSeq
  }

  /** Evaluate a MixedVec mapping once during elaboration, then distribute its elements. */
  def vecMap[T <: Data, U <: Data](in: FlowIO[T], f: T => MixedVec[U])(using SourceInfo): IndexedSeq[FlowIO[U]] =
    vecSplit(in.payloadMap(f))
}
