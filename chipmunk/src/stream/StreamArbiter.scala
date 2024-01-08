package chipmunk
package stream

import chisel3._
import chisel3.util._

object StreamArbiter {
  def roundRobin[T <: Data](ins: Seq[StreamIO[T]]): StreamIO[T] = {
    val uArb = Module(new RRArbiter(ins.head.bits, ins.length))
    (uArb.io.in zip ins).foreach { x =>
      x._1.valid := x._2.valid
      x._1.bits  := x._2.bits
      x._2.ready := x._1.ready
    }
    uArb.io.out.toStream
  }

  def lowerFirst[T <: Data](ins: Seq[StreamIO[T]]): StreamIO[T] = {
    val uArb = Module(new Arbiter(ins.head.bits, ins.length))
    (uArb.io.in zip ins).foreach { x =>
      x._1.valid := x._2.valid
      x._1.bits  := x._2.bits
      x._2.ready := x._1.ready
    }
    uArb.io.out.toStream
  }
}

/** Combine a stream and a flow to a new flow. If both input sources fire, the flow will be preferred. */
object StreamFlowArbiter {
  def apply[T <: Data](inStream: StreamIO[T], inFlow: FlowIO[T]): FlowIO[T] = {
    val output = Wire(Flow(inFlow))
    output.valid   := inFlow.valid || inStream.valid
    inStream.ready := !inFlow.valid
    output.bits    := Mux(inFlow.valid, inFlow.bits, inStream.bits)
    output
  }
}
