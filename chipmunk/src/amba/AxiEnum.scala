package chipmunk
package amba

import chisel3._

object AxiBurstType extends ChiselEnum {
  val BURST_FIXED = Value(0.U)
  val BURST_INCR  = Value(1.U)
  val BURST_WRAP  = Value(2.U)
}

object AxiBurstSize extends ChiselEnum {
  val SIZE1   = Value(0.U)
  val SIZE2   = Value(1.U)
  val SIZE4   = Value(2.U)
  val SIZE8   = Value(3.U)
  val SIZE16  = Value(4.U)
  val SIZE32  = Value(5.U)
  val SIZE64  = Value(6.U)
  val SIZE128 = Value(7.U)
}

object AxiResp extends ChiselEnum {
  val RESP_OKAY   = Value(0.U)
  val RESP_EXOKAY = Value(1.U)
  val RESP_SLVERR = Value(2.U)
  val RESP_DECERR = Value(3.U)
}
