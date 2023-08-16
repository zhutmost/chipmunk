package chipmunk

import chisel3._
import chisel3.experimental.{requireIsChiselType, requireIsHardware}
import chisel3.util._

private[chipmunk] class RegNegBbox(width: Int, haveReset: Boolean, isResetAsync: Boolean = true)
    extends BlackBox(Map("WIDTH" -> width, "RESET_ASYNC" -> isResetAsync.toString))
    with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = if (haveReset) Some(Input(Reset())) else None
    val init  = if (haveReset) Some(Input(UInt(width.W))) else None
    val en    = Input(Bool())
    val d     = Input(UInt(width.W))
    val q     = Output(UInt(width.W))
  })

  override def desiredName = "Chipmunk_" + (if (haveReset) "RegNegInit" else "RegNeg")
  addResource(f"/regneg/$desiredName.sv")
}

/** Register triggered on falling clock edge. This Module is just a wrapper of [[RegNegBbox]], so that users don't need
  * to assign clock and reset explicitly.
  *
  * @param gen
  *   The Chisel type of the register.
  * @param haveReset
  *   Whether the register has a reset. If true, the register will have a active-high reset.
  * @param isResetAsync
  *   Whether the reset is asynchronous. Chisel cannot infer this, so users should specify it.
  */
private[chipmunk] class RegNeg[T <: Data](gen: T, haveReset: Boolean, isResetAsync: Boolean = true) extends Module {
  val io = IO(new Bundle {
    val init = Input(gen)
    val en   = Input(Bool())
    val d    = Input(gen)
    val q    = Output(gen)
  })
  val width = io.d.getWidth

  val uRegNegBbox = Module(new RegNegBbox(width, haveReset, isResetAsync))
  uRegNegBbox.io.clock := clock
  uRegNegBbox.io.en    := io.en
  uRegNegBbox.io.d     := io.d.asUInt
  io.q                 := uRegNegBbox.io.q.asTypeOf(gen)

  uRegNegBbox.io.reset.foreach(_ := reset)
  uRegNegBbox.io.init.foreach(_ := io.init.asUInt)
}

object RegNegNext {

  /** Returns a falling-edge triggered register with no reset initialization.
    *
    * It has a similar function to [[chisel3.RegNext]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val regNeg = RegNegNext(nextVal)
    *   }}}
    */
  def apply[T <: Data](next: T): T = {
    RegNegEnable(next, true.B)
  }

  /** Returns a falling-edge triggered register with active-high reset initialization. The reset can be synchronous or
    * asynchronous, according to its type.
    *
    * It has a similar function to [[chisel3.RegNext]] but it is triggered on the falling clock edge.
    *
    * @param isResetAsync
    *   Whether the reset is asynchronous. Chisel cannot infer this from the Scala context, so users should specify it.
    * @example
    *   {{{
    * val regNeg = RegNegNext(nextVal)
    *   }}}
    */
  def apply[T <: Data](next: T, init: T, isResetAsync: Boolean = true): T = {
    RegNegEnable(next, init, true.B, isResetAsync)
  }
}

object RegNegEnable {

  /** Returns a falling-edge triggered register with update enable gate and no reset initialization.
    *
    * It has a similar function to [[chisel3.util.RegEnable]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val regNeg = RegNegEnable(nextVal, ena)
    *   }}}
    */
  def apply[T <: Data](next: T, enable: Bool): T = {
    requireIsHardware(next, "RegNegEnable.next")
    requireIsHardware(enable, "RegNegEnable.enable")

    val reg = Module(new RegNeg(chiselTypeOf(next), haveReset = false))
    reg.io.en := enable
    reg.io.d  := next
    reg.io.q
  }

  /** Returns a falling-edge triggered register with update enable gate and active-high reset initialization. The reset
    * can be synchronous or asynchronous, according to its type.
    *
    * It has a similar function to [[chisel3.util.RegEnable]] but it is triggered on the falling clock edge.
    *
    * @param isResetAsync
    *   Whether the reset is asynchronous. Chisel cannot infer this from the Scala context, so users should specify it.
    * @example
    *   {{{
    * val regNeg = RegNegEnable(nextVal, initVal, ena)
    *   }}}
    */
  def apply[T <: Data](next: T, init: T, enable: Bool, isResetAsync: Boolean = true): T = {
    requireIsHardware(next, "RegNegEnable.next")
    requireIsHardware(init, "RegNegEnable.init")
    requireIsHardware(enable, "RegNegEnable.enable")

    val reg = Module(new RegNeg(chiselTypeOf(next), haveReset = true, isResetAsync = isResetAsync))
    reg.io.en   := enable
    reg.io.d    := next
    reg.io.init := init
    reg.io.q
  }
}
