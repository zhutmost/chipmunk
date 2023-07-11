package chipmunk

import chisel3._
import chisel3.experimental.requireIsHardware
import chisel3.util._

private[chipmunk] class RegNegBbox(width: Int, haveReset: Boolean, isResetAsync: Boolean = true)
    extends BlackBox(Map("WIDTH" -> width, "RESET_EXIST" -> haveReset.toString, "RESET_ASYNC" -> isResetAsync.toString))
    with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val en    = Input(Bool())
    val init  = Input(UInt(width.W))
    val d     = Input(UInt(width.W))
    val q     = Output(UInt(width.W))
  })
  override val desiredName = "Chipmunk_RegNegBbox"

  setInline(
    f"$desiredName.sv",
    s"""
       |module $desiredName #(
       |  parameter WIDTH = 1,
       |  parameter RESET_EXIST = "true",
       |  parameter RESET_ASYNC = "true"
       |)(
       |  input  wire clock,
       |  input  wire reset,
       |  input  wire en,
       |  input  wire [WIDTH-1:0] init,
       |  input  wire [WIDTH-1:0] d,
       |  output wire [WIDTH-1:0] q
       |);
       |  reg [WIDTH-1:0] r;
       |  assign q = r;
       |
       |  if (RESET_EXIST == "true" && RESET_ASYNC == "true") begin
       |    always @(negedge clock or posedge reset) begin
       |      if (reset)   r <= init;
       |      else if (en) r <= d;
       |    end
       |  end else if (RESET_EXIST == "true" && RESET_ASYNC == "false") begin
       |    always @(negedge clock) begin
       |      if (reset)   r <= init;
       |      else if (en) r <= d;
       |    end
       |  end else begin
       |    // Signal reset/init is unconnected.
       |    always @(negedge clock) begin
       |      if (en) r <= d;
       |    end
       |  end
       |
       |endmodule : $desiredName
       |""".stripMargin
  )
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

  val width      = io.d.getWidth
  val resetValue = if (haveReset) io.init.asUInt else 0.U(width.W)

  val resetWire = (haveReset, isResetAsync) match {
    case (true, true)   => reset.asAsyncReset
    case (true, false)  => reset.asBool
    case (false, true)  => false.B.asAsyncReset
    case (false, false) => false.B
  }

  val uRegNegBbox = Module(new RegNegBbox(width, haveReset, isResetAsync))
  uRegNegBbox.io.clock := clock
  uRegNegBbox.io.reset := resetWire
  uRegNegBbox.io.init  := resetValue
  uRegNegBbox.io.en    := io.en
  uRegNegBbox.io.d     := io.d.asUInt
  io.q                 := uRegNegBbox.io.q.asTypeOf(gen)
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
    requireIsHardware(next, "reg next")
    requireIsHardware(enable, "reg enable")

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
    requireIsHardware(next, "reg next")
    requireIsHardware(init, "reg init")
    requireIsHardware(enable, "reg enable")

    val reg = Module(new RegNeg(chiselTypeOf(next), haveReset = true, isResetAsync = isResetAsync))
    reg.io.en   := enable
    reg.io.d    := next
    reg.io.init := init
    reg.io.q
  }
}
