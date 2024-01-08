package chipmunk

import chisel3._
import chisel3.util._

package object stream {
  implicit class ImplicitStreamCreator[T <: Data](d: DecoupledIO[T]) {
    def toStream: StreamIO[T] = {
      val ret = Wire(new StreamIO(chiselTypeOf(d.bits)))
      ret.bits  := d.bits
      ret.valid := d.valid
      d.ready   := ret.ready
      ret
    }
  }
}
