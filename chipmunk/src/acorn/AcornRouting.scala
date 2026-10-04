package chipmunk
package acorn

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{log2Ceil, PriorityEncoder, Queue}

import chipmunk.stream.*

private[acorn] object AcornRouting {
  private def transferredIndex[T <: Data](ports: scala.collection.Seq[StreamIO[T]])(using SourceInfo): UInt =
    if (ports.size == 1) 0.U else PriorityEncoder(VecInit(ports.map(_.fire).toVector).asUInt)

  def merge[C <: Data, R <: Data](
    ins: scala.collection.Seq[StreamIO[C]],
    out: StreamIO[C],
    response: StreamIO[R],
    responses: scala.collection.Seq[StreamIO[R]],
    depth: Int,
    arbitration: AcornArbitration,
  )(using SourceInfo): Unit = {
    val owners = Module(new Queue(UInt(math.max(1, log2Ceil(ins.size)).W), depth, pipe = false, flow = false))
    // Capacity depends only on registered occupancy. While a command is stalled, no other command can consume it.
    val eligible = ins.map(_.continueWhen(owners.io.enq.ready))
    val selected = arbitration match {
      case AcornArbitration.RoundRobin => StreamArbiter.roundRobin(eligible)
      case AcornArbitration.LowerFirst => StreamArbiter.lowerFirst(eligible)
    }
    out << selected
    owners.io.enq.valid := out.fire
    owners.io.enq.bits  := transferredIndex(ins)

    val routed = StreamDemux(response, Stream.from(owners.io.deq), ins.size)
    responses.zip(routed).foreach { case (sink, source) => sink << source }
  }

  def route[C <: Data, R <: Data](
    in: StreamIO[C],
    outs: scala.collection.Seq[StreamIO[C]],
    select: UInt,
    responses: scala.collection.Seq[StreamIO[R]],
    response: StreamIO[R],
    depth: Int,
  )(using SourceInfo): Unit = {
    val targets = Module(new Queue(UInt(math.max(1, log2Ceil(outs.size)).W), depth, pipe = false, flow = false))
    val routed  = StreamDemux(in.continueWhen(targets.io.enq.ready), select, outs.size)
    outs.zip(routed).foreach { case (sink, source) => sink << source }
    targets.io.enq.valid := in.fire
    // Record the destination that actually transferred, including a selector held by StreamDemux during a stall.
    targets.io.enq.bits := transferredIndex(outs)
    response << StreamMux(Stream.from(targets.io.deq), responses)
  }
}
