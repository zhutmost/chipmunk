package chipmunk

import chisel3.*
import chisel3.experimental.{requireIsHardware, SourceInfo}
import chisel3.reflect.DataMirror

/** External module implementing a falling-edge register without reset.
  *
  * @param bitwidth
  *   bitwidth of the stored data; must be positive
  */
private[chipmunk] final class RegNegBbox(bitwidth: Int) extends ExtModule(Map("WIDTH" -> IntParam(BigInt(bitwidth)))) {
  require(bitwidth > 0, "RegNegBbox requires a positive data bitwidth.")

  val io = FlatIO(new Bundle {
    val clock = Input(Clock())
    val en    = Input(Bool())
    val d     = Input(UInt(bitwidth.W))
    val q     = Output(UInt(bitwidth.W))
  })

  override def desiredName: String = "Chipmunk_RegNeg"

  addResource(s"/regneg/$desiredName.sv")
}

/** External module implementing a falling-edge register with reset.
  *
  * INIT is a static SystemVerilog parameter rather than a runtime input.
  *
  * @param bitwidth
  *   bitwidth of the stored data; must be positive
  * @param isResetAsync
  *   true for asynchronous reset, false for synchronous reset
  * @param initBits
  *   unsigned bit pattern of the reset value
  */
private[chipmunk] final class RegNegInitBbox(bitwidth: Int, isResetAsync: Boolean, initBits: BigInt)
    extends ExtModule(
      Map(
        "WIDTH"       -> IntParam(BigInt(bitwidth)),
        "RESET_ASYNC" -> IntParam(if (isResetAsync) BigInt(1) else BigInt(0)),
        "INIT"        -> RawParam(s"${bitwidth}'h${initBits.toString(16)}")
      )
    ) {
  require(bitwidth > 0, "RegNegInitBbox requires a positive data bitwidth.")
  require(
    initBits >= 0 && initBits.bitLength <= bitwidth,
    "RegNegInitBbox.initBits must be an unsigned value fitting bitwidth."
  )

  val io = FlatIO(new Bundle {
    val clock = Input(Clock())

    // Give the external port the same concrete reset type as the wrapper.
    val reset: Reset =
      if (isResetAsync) Input(AsyncReset()) else Input(Bool())

    val en = Input(Bool())
    val d  = Input(UInt(bitwidth.W))
    val q  = Output(UInt(bitwidth.W))
  })

  override def desiredName: String = "Chipmunk_RegNegInit"

  addResource(s"/regneg/$desiredName.sv")
}

/** Connects a packable Chisel data type to a falling-edge register.
  *
  * The data must have a known, positive bitwidth and support asUInt/asTypeOf. initBits is None for a register without
  * reset. When it is defined, isResetAsync determines the wrapper's concrete reset type.
  */
private[chipmunk] final class RegNeg[T <: Data](gen: T, initBits: Option[BigInt], isResetAsync: Boolean)
    extends Module {

  // Module reads resetType while constructing its implicit reset port.
  // Therefore, the choice must be available from the constructor parameters.
  override def resetType: Module.ResetType.Type =
    initBits match {
      case Some(_) if isResetAsync => Module.ResetType.Asynchronous
      case Some(_)                 => Module.ResetType.Synchronous
      case None                    => Module.ResetType.Default
    }

  require(gen.isWidthKnown && gen.getWidth > 0, "RegNeg data must have a known, positive bitwidth.")

  val io = IO(new Bundle {
    val en = Input(Bool())
    val d  = Input(gen)
    val q  = Output(gen)
  })

  private val bitwidth = gen.getWidth
  initBits match {
    case Some(bits) =>
      val reg = Module(new RegNegInitBbox(bitwidth, isResetAsync, bits))
      reg.io.clock := clock
      reg.io.reset := reset
      reg.io.en    := io.en
      reg.io.d     := io.d.asUInt
      io.q         := reg.io.q.asTypeOf(gen)

    case None =>
      val reg = Module(new RegNegBbox(bitwidth))
      reg.io.clock := clock
      reg.io.en    := io.en
      reg.io.d     := io.d.asUInt
      io.q         := reg.io.q.asTypeOf(gen)
  }
}

object RegNegNext {

  /** Returns a falling-edge register without reset.
    *
    * It has a similar function to [[chisel3.RegNext]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val q = RegNegNext(nextValue)
    *   }}}
    */
  def apply[T <: Data](next: T)(using SourceInfo): T =
    RegNegEnable(next, true.B)

  /** Returns a falling-edge register with active-high reset.
    *
    * It has a similar function to [[chisel3.RegNext]] but it is triggered on the falling clock edge.
    *
    * The reset value must be a complete Chisel literal with the same type and bitwidth as next. The reset mode must be
    * specified explicitly.
    *
    * @param next
    *   value captured on the falling clock edge
    * @param init
    *   constant value loaded on reset
    * @param isResetAsync
    *   true for asynchronous reset, false for synchronous reset
    * @example
    *   {{{
    * val q = RegNegNext(nextValue, 0.U(8.W), isResetAsync = false)
    *   }}}
    */
  def apply[T <: Data](next: T, init: T, isResetAsync: Boolean)(using SourceInfo): T =
    RegNegEnable(next, init, true.B, isResetAsync)
}

object RegNegEnable {

  /** Returns a falling-edge register with enable and without reset.
    *
    * It has a similar function to [[chisel3.util.RegEnable]] but it is triggered on the falling clock edge.
    *
    * @example
    *   {{{
    * val q = RegNegEnable(nextValue, enable)
    *   }}}
    */
  def apply[T <: Data](next: T, enable: Bool)(using SourceInfo): T = {
    requireIsHardware(next, "RegNegEnable.next")
    requireIsHardware(enable, "RegNegEnable.enable")

    val reg = Module(new RegNeg(chiselTypeOf(next), None, isResetAsync = false))
    reg.io.en := enable
    reg.io.d  := next
    reg.io.q
  }

  /** Returns a falling-edge register with enable and active-high reset.
    *
    * It has a similar function to [[chisel3.util.RegEnable]] but it is triggered on the falling clock edge.
    *
    * The reset value must be a complete, static Chisel literal with the same type and bitwidth as next. In particular,
    * a runtime signal cannot be used as init.
    *
    * @param next
    *   value captured when enable is true
    * @param init
    *   constant value loaded on reset
    * @param enable
    *   register update enable
    * @param isResetAsync
    *   Whether the reset is asynchronous. Chisel cannot infer this from the Scala context, so users should specify it.
    * @example
    *   {{{
    * val q = RegNegEnable(nextValue, 0.U(8.W), enable, isResetAsync = false)
    *   }}}
    */
  def apply[T <: Data](next: T, init: T, enable: Bool, isResetAsync: Boolean)(using SourceInfo): T = {
    requireIsHardware(next, "RegNegEnable.next")
    requireIsHardware(init, "RegNegEnable.init")
    requireIsHardware(enable, "RegNegEnable.enable")

    require(next.isWidthKnown && next.getWidth > 0, "RegNegEnable.next must have a known, positive bitwidth.")
    require(
      init.isWidthKnown && init.getWidth == next.getWidth,
      "RegNegEnable.init must have the same bitwidth as next."
    )
    require(DataMirror.checkTypeEquivalence(next, init), "RegNegEnable.init must have the same Chisel type as next.")
    require(init.isLit, "RegNegEnable.init must be a Chisel literal.")

    val bitwidth = next.getWidth

    // Mask negative SInt literals into their bitwidth-wide two's-complement
    // representation. Aggregate litValue uses the corresponding packed order.
    // litValue also rejects aggregate literals containing DontCare.
    val mask     = (BigInt(1) << bitwidth) - 1
    val initBits = init.litValue & mask

    val reg = Module(new RegNeg(chiselTypeOf(next), Some(initBits), isResetAsync))
    reg.io.en := enable
    reg.io.d  := next
    reg.io.q
  }
}
