package chipmunk
package stream

import chisel3._
import chisel3.util._

/** Multiplex multiple streams into a single one, always only processing one at a time. */
object StreamMux {
  def apply[T <: Data](select: UInt, ins: Vec[StreamIO[T]]): StreamIO[T] = {
    val uMux = Module(new StreamMux(ins(0).bits, ins.length))
    (uMux.io.ins zip ins).foreach(x => x._1 << x._2)
    uMux.io.select := select
    uMux.io.out
  }

  def apply[T <: Data](select: StreamIO[UInt], ins: Vec[StreamIO[T]]): StreamIO[T] = {
    val uMux = Module(new StreamMux(ins(0).bits, ins.length))
    (uMux.io.ins zip ins).foreach(x => x._1 << x._2)
    select >> uMux.io.createSelectStream()
    uMux.io.out
  }
}

class StreamMux[T <: Data](gen: T, num: Int) extends Module {
  val io = IO(new Bundle {
    val select: UInt          = Input(UInt(log2Ceil(num).W))
    val ins: Vec[StreamIO[T]] = Vec(num, Slave(Stream(gen)))
    val out: StreamIO[T]      = Master(Stream(gen))

    def createSelectStream(): StreamIO[UInt] = {
      val stream  = Wire(Stream(select))
      val regFlow = stream.haltWhen(out.isPending).toFlow(readyFreeRun = true)
      select := RegEnable(regFlow.bits, 0.U, regFlow.fire)
      stream
    }
  })
  for ((input, index) <- io.ins.zipWithIndex) {
    input.ready := io.select === index.U && io.out.ready
  }
  io.out.valid := io.ins(io.select).valid
  io.out.bits  := io.ins(io.select).bits
}
