package chipmunk
package stream

import scala.collection.Seq

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{log2Ceil, Mux1H}

/** Select one input stream without buffering its payload. */
object StreamMux {

  /** Use an external selector. Once output is offered and stalled, its input choice is held until transfer.
    *
    * An out-of-range selection stalls all inputs and makes output invalid. A single input still honors selection zero.
    */
  def apply[T <: Data](select: UInt, ins: Seq[StreamIO[T]])(using SourceInfo): StreamIO[T] = {
    require(ins.nonEmpty, "StreamMux requires at least one input.")
    val out = Wire(Stream.like(ins.head))
    connect(select, ins, out)
    out
  }

  /** Consume one selection token together with each selected data transaction.
    *
    * Both sources must obey the StreamIO contract. Selection tokens are not updates to a configuration register.
    * Out-of-range tokens stall without consuming either source.
    */
  def apply[T <: Data](select: StreamIO[UInt], ins: Seq[StreamIO[T]])(using SourceInfo): StreamIO[T] = {
    require(ins.nonEmpty, "StreamMux requires at least one input.")
    val out     = Wire(Stream.like(ins.head))
    val matches = ins.indices.map(index => select.bits === index.U)
    val offered = ins.zip(matches).map { case (in, hit) => in.valid && hit }.reduce(_ || _)

    out.valid    := select.valid && offered
    out.bits     := Mux1H(matches, ins.map(_.bits).toVector)
    select.ready := out.ready && offered
    ins.zip(matches).foreach { case (in, hit) => in.ready := out.ready && select.valid && hit }
    out
  }

  private[stream] def connect[T <: Data](select: UInt, ins: Seq[StreamIO[T]], out: StreamIO[T])(using
    SourceInfo
  ): Unit = {
    val locked   = RegInit(false.B)
    val held     = Reg(UInt(math.max(1, log2Ceil(ins.length)).W))
    val selected = Mux(locked, held, select)
    val matches  = ins.indices.map(index => selected === index.U)

    out.valid := ins.zip(matches).map { case (in, hit) => in.valid && hit }.reduce[Bool](_ || _)
    out.bits  := Mux1H(matches, ins.map(_.bits).toVector)
    ins.zip(matches).foreach { case (in, hit) => in.ready := out.ready && hit }

    when(out.fire) {
      locked := false.B
    }.elsewhen(out.valid && !out.ready) {
      locked := true.B
      held   := selected
    }
  }
}

/** Module form of [[StreamMux]], using an external selector and preserving an offered choice while stalled. */
class StreamMux[T <: Data](gen: T, num: Int) extends Module {
  require(num >= 1, "StreamMux requires at least one input.")

  val io = IO(new Bundle {
    val select = Input(UInt(math.max(1, log2Ceil(num)).W))
    val ins    = Vec(num, Slave(Stream(gen)))
    val out    = Master(Stream(gen))
  })

  StreamMux.connect(io.select, io.ins, io.out)
}
