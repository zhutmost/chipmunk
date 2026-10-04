package chipmunk
package stream

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{log2Ceil, Mux1H}

/** Route each input transaction to exactly one output without buffering its payload. */
object StreamDemux {

  /** Use an external selector, holding an offered destination until transfer.
    *
    * Out-of-range selections stall input and make every output invalid. Full selector width is checked, so high bits
    * are not silently truncated by this factory.
    */
  def apply[T <: Data](in: StreamIO[T], select: UInt, num: Int)(using SourceInfo): Vec[StreamIO[T]] = {
    require(num >= 1, "StreamDemux requires at least one output.")
    val outs = Wire(Vec(num, Stream.like(in)))
    connect(in, select, outs)
    outs
  }

  /** Consume one destination token together with each input transaction.
    *
    * Both sources must obey the StreamIO contract. An out-of-range token stalls without consuming either source.
    */
  def apply[T <: Data](in: StreamIO[T], select: StreamIO[UInt], num: Int)(using SourceInfo): Vec[StreamIO[T]] = {
    require(num >= 1, "StreamDemux requires at least one output.")
    val outs    = Wire(Vec(num, Stream.like(in)))
    val matches = outs.indices.map(index => select.bits === index.U)
    val ready   = Mux1H(matches, outs.map(_.ready))

    in.ready     := select.valid && ready
    select.ready := in.valid && ready
    outs.zip(matches).foreach { case (out, hit) =>
      out.valid := in.valid && select.valid && hit
      out.bits  := in.bits
    }
    outs
  }

  private[stream] def connect[T <: Data](in: StreamIO[T], select: UInt, outs: Vec[StreamIO[T]])(using
    SourceInfo
  ): Unit = {
    val locked   = RegInit(false.B)
    val held     = Reg(UInt(math.max(1, log2Ceil(outs.length)).W))
    val selected = Mux(locked, held, select)
    val matches  = outs.indices.map(index => selected === index.U)

    in.ready := Mux1H(matches, outs.map(_.ready))
    outs.zip(matches).foreach { case (out, hit) =>
      out.valid := in.valid && hit
      out.bits  := in.bits
    }

    when(in.fire) {
      locked := false.B
    }.elsewhen(in.valid && matches.reduce(_ || _) && !in.ready) {
      locked := true.B
      held   := selected
    }
  }
}

/** Module form of [[StreamDemux]], using an external selector. */
class StreamDemux[T <: Data](gen: T, num: Int) extends Module {
  require(num >= 1, "StreamDemux requires at least one output.")

  val io = IO(new Bundle {
    val select = Input(UInt(math.max(1, log2Ceil(num)).W))
    val in     = Slave(Stream(gen))
    val outs   = Vec(num, Master(Stream(gen)))
  })

  StreamDemux.connect(io.in, io.select, io.outs)
}
