package chipmunk
package stream

import scala.collection.Seq

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{Mux1H, PriorityEncoder}

/** Arbitration of ordered inputs whose producers obey the StreamIO stability contract. */
object StreamArbiter {

  /** Round-robin arbitration, with deterministic reset priority and no added latency.
    *
    * The last grant resets to zero, so input one is preferred initially when all inputs are valid. The pointer advances
    * only on output transfer. A choice offered to a stalled consumer is held until transfer.
    */
  def roundRobin[T <: Data](ins: Seq[StreamIO[T]])(using SourceInfo): StreamIO[T] =
    arbitrate(ins, roundRobin = true)

  /** Give lower input indices priority, holding an offered choice until transfer. */
  def lowerFirst[T <: Data](ins: Seq[StreamIO[T]])(using SourceInfo): StreamIO[T] =
    arbitrate(ins, roundRobin = false)

  private def arbitrate[T <: Data](ins: Seq[StreamIO[T]], roundRobin: Boolean)(using SourceInfo): StreamIO[T] = {
    require(ins.nonEmpty, "An arbiter requires at least one input.")

    if (ins.length == 1) {
      ins.head
    } else {
      val out        = Wire(Stream.like(ins.head))
      val width      = chisel3.util.log2Ceil(ins.length)
      val locked     = RegInit(false.B)
      val heldChoice = Reg(UInt(width.W))
      val choice     = Wire(UInt(width.W))
      val selected   = Mux(locked, heldChoice, choice)
      val requests   = VecInit(ins.map(_.valid).toVector)

      if (roundRobin) {
        val lastGrant = RegInit(0.U(width.W))
        val masked    = VecInit(ins.zipWithIndex.map { case (in, index) => in.valid && index.U > lastGrant }.toVector)
        choice := PriorityEncoder(Mux(masked.asUInt.orR, masked.asUInt, requests.asUInt))
        when(out.fire) {
          lastGrant := selected
        }
      } else {
        choice := PriorityEncoder(requests.asUInt)
      }

      val grants = ins.indices.map(index => selected === index.U)
      out.valid := ins.zip(grants).map { case (in, grant) => in.valid && grant }.reduce[Bool](_ || _)
      out.bits  := Mux1H(grants, ins.map(_.bits).toVector)
      ins.zip(grants).foreach { case (in, grant) => in.ready := out.ready && grant }

      when(out.fire) {
        locked := false.B
      }.elsewhen(out.valid && !out.ready) {
        locked     := true.B
        heldChoice := selected
      }

      out
    }
  }
}

/** Merge a Stream and a Flow into an unbackpressured Flow, giving the Flow priority. */
object StreamFlowArbiter {

  /** The Flow consumer must accept every valid cycle; the Stream waits whenever the input Flow is valid. */
  def apply[T <: Data](inStream: StreamIO[T], inFlow: FlowIO[T])(using SourceInfo): FlowIO[T] = {
    val out = Wire(Flow.like(inFlow))
    out.valid      := inFlow.valid || inStream.valid
    out.bits       := Mux(inFlow.valid, inFlow.bits, inStream.bits)
    inStream.ready := !inFlow.valid
    out.readOnly
  }
}
