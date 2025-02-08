package chipmunk
package stream

import chisel3._
import chisel3.util._

/** Wrapper of chisel's builtin RRArbiter, but its grant register has an initialized value. */
private[chipmunk] class StreamArbiterRR[T <: Data](gen: T, n: Int) extends RRArbiter(gen, n) {
  override lazy val lastGrant: UInt = RegEnable(io.chosen, 0.U, io.out.fire)
}

object StreamArbiter {
  def roundRobin[T <: Data](ins: Seq[StreamIO[T]]): StreamIO[T] = {
    val uArb = Module(new StreamArbiterRR(chiselTypeOf(ins.head.bits), ins.length))
    (uArb.io.in zip ins).foreach { x =>
      x._1.valid := x._2.valid
      x._1.bits  := x._2.bits
      x._2.ready := x._1.ready
    }
    uArb.io.out.toStream
  }

  def lowerFirst[T <: Data](ins: Seq[StreamIO[T]]): StreamIO[T] = {
    val uArb = Module(new Arbiter(chiselTypeOf(ins.head.bits), ins.length))
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
