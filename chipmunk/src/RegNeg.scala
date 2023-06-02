package chipmunk

import chisel3._
import chisel3.experimental.requireIsHardware
import chisel3.util._

private[chipmunk] class RegNeg(width: Int, haveReset: Boolean, resetAsync: Boolean = true, resetValue: UInt = 0.U)
    extends BlackBox(
      Map(
        "WIDTH"       -> width,
        "RESET_EXIST" -> haveReset.toString,
        "RESET_ASYNC" -> resetAsync.toString,
        "RESET_VALUE" -> resetValue.litValue
      )
    )
    with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val en    = Input(Bool())
    val d     = Input(UInt(width.W))
    val q     = Output(UInt(width.W))
  })

  assert(width >= 1, f"RegNeg width must be >= 1 but got $width.")

  setInline(
    f"RegNeg.sv",
    s"""
       |module RegNeg #(
       |  parameter WIDTH = 1,
       |  parameter RESET_EXIST = "true",
       |  parameter RESET_ASYNC = "true",
       |  parameter RESET_VALUE = 0
       |)(
       |  input  wire clock,
       |  input  wire reset,
       |  input  wire en,
       |  input  wire [WIDTH-1:0] d,
       |  output wire [WIDTH-1:0] q
       |);
       |  reg [WIDTH-1:0] r;
       |  assign q = r;
       |
       |  generate
       |    if (RESET_EXIST == "true" && RESET_ASYNC == "true") begin: gRegNeg_AsyncReset
       |      always @(posedge clock or posedge reset) begin
       |        if (reset) r <= RESET_VALUE;
       |        else if (en) r <= d;
       |      end
       |    end else if (RESET_EXIST == "true" && RESET_ASYNC == "false") begin: gRegNeg_syncReset
       |      always @(posedge clock) begin
       |        if (reset) r <= RESET_VALUE;
       |        else if (en) r <= d;
       |      end
       |    end else begin: gRegNeg_noReset
       |      // Signal reset is unconnected.
       |      always @(posedge clock) begin
       |        if (en) r <= d;
       |      end
       |    end
       |  endgenerate
       |
       |endmodule : RegNeg
       |""".stripMargin
  )
}

object RegNegNext {

  /** Returns a falling-edge triggered register with no reset initialization.
    *
    * It has a similar function to [[chisel3.RegNext]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val regNeg = RegNegNext(nextVal)(clock)
    *   }}}
    */
  def apply[T <: Data](next: T)(implicit clock: Clock): T = {
    RegNegEnable(next, true.B)(clock)
  }

  /** Returns a falling-edge triggered register with reset initialization. The reset can be synchronous or asynchronous,
    * according to its type.
    *
    * It has a similar function to [[chisel3.RegNext]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val regNeg = RegNegNext(nextVal)(clock, reset)
    *   }}}
    */
  def apply[T <: Data](next: T, init: T)(implicit clock: Clock, reset: Reset): T = {
    RegNegEnable(next, init, true.B)(clock, reset)
  }
}

object RegNegEnable {

  /** Returns a falling-edge triggered register with update enable gate and no reset initialization.
    *
    * It has a similar function to [[chisel3.util.RegEnable]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val regNeg = RegNegEnable(nextVal, ena)(clock)
    *   }}}
    */
  def apply[T <: Data](next: T, enable: Bool)(implicit clock: Clock): T = {
    requireIsHardware(next, "reg next")
    requireIsHardware(enable, "reg enable")

    val reg = Module(new RegNeg(next.getWidth, haveReset = false))
    reg.io.en    := enable
    reg.io.clock := clock
    reg.io.reset := false.B.asAsyncReset // Unconnected actually
    reg.io.d     := next.asUInt
    reg.io.q.asTypeOf(next)
  }

  /** Returns a falling-edge triggered register with update enable gate and reset initialization. The reset can be
    * synchronous or asynchronous, according to its type.
    *
    * It has a similar function to [[chisel3.util.RegEnable]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val regNeg = RegNegEnable(nextVal, initVal, ena)(clock, reset)
    *   }}}
    */
  def apply[T <: Data](next: T, init: T, enable: Bool)(implicit clock: Clock, reset: Reset): T = {
    requireIsHardware(next, "reg next")
    requireIsHardware(init, "reg init")
    requireIsHardware(enable, "reg enable")
    val isAsyncReset = reset.isInstanceOf[AsyncReset]

    val reg = Module(new RegNeg(next.getWidth, haveReset = true, resetAsync = isAsyncReset, resetValue = init.asUInt))
    reg.io.en    := enable
    reg.io.clock := clock
    reg.io.reset := reset
    reg.io.d     := next.asUInt
    reg.io.q.asTypeOf(next)
  }
}
