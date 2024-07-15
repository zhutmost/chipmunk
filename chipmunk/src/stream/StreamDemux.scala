package chipmunk
package stream

import chisel3._
import chisel3.util._

/** Demultiplex one stream into multiple output streams, always selecting only one at a time. */
object StreamDemux {
  def apply[T <: Data](in: StreamIO[T], select: UInt, num: Int): Vec[StreamIO[T]] = {
    val c = Module(new StreamDemux(in.bits.cloneType, num))
    c.io.in << in
    c.io.select := select
    c.io.outs
  }

  def apply[T <: Data](in: StreamIO[T], select: StreamIO[UInt], num: Int): Vec[StreamIO[T]] = {
    val c = Module(new StreamDemux(in.bits.cloneType, num))
    c.io.in << in
    select >> c.io.createSelectStream()
    c.io.outs
  }
}

class StreamDemux[T <: Data](dataType: T, num: Int) extends Module {
  val io = IO(new Bundle {
    val select = Input(UInt(log2Ceil(num).W))
    val in     = Slave(Stream(dataType))
    val outs   = Vec(num, Master(Stream(dataType)))

    def createSelectStream(): StreamIO[UInt] = {
      val stream  = Wire(Stream(UInt(log2Ceil(num).W)))
      val regFlow = stream.haltWhen(in.isPending).toFlow(readyFreeRun = true)
      select := RegEnable(regFlow.bits, 0.U, regFlow.fire)
      stream
    }
  })
  io.in.ready := false.B
  for (i <- 0 until num) {
    io.outs(i).bits := io.in.bits
    when(io.select =/= i.U) {
      io.outs(i).valid := false.B
    } otherwise {
      io.outs(i).valid := io.in.valid
      io.in.ready      := io.outs(i).ready
    }
  }
}
