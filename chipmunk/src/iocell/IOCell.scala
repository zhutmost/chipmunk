package chipmunk

package iocell

import chisel3._
import chisel3.experimental.{Analog, ExtModule, attach}

abstract class IOCell extends ExtModule

abstract class IOCellLibrary {
  case class TriStateIOCell(hasInputEnable: Boolean, hasOutputEnable: Boolean, hasPullEnable: Boolean) extends IOCell {
    val pad = IO(Analog(1.W))                                        // wire-bonding pad
    val out = IO(Output(Bool()))                                     // data signal from pad to core
    val in  = IO(Input(Bool()))                                      // data signal from core to pad
    val oe  = if (hasOutputEnable) Some(IO(Input(Bool()))) else None // output enable, active low
    val ie  = if (hasInputEnable) Some(IO(Input(Bool()))) else None  // output enable, active low
    val pe  = if (hasPullEnable) Some(IO(Input(Bool()))) else None   // pull up/down enable, active low
  }

  case class AnalogIOCell() extends IOCell {
    val inout = IO(Analog(1.W))
  }

  /** Instantiate an analog I/O cell, and connect it */
  def createAnalogIOCell(cell: => AnalogIOCell)(core: Analog): AnalogIOCell = {
    assert(core.getWidth == 1, "Chisel doesn't support indexed Analog, so that the core signal width must be 1.")
    val uIOCell = Module(cell)
    attach(uIOCell.inout, core)
    uIOCell
  }

  private def connectTriStateIOCell(
    cell: => TriStateIOCell
  )(pad: Analog, core: Bool, out: Bool, outEnable: Bool, pullEnable: Bool, inEnable: Bool): TriStateIOCell = {
    val uIOCell = Module(cell)
    attach(uIOCell.pad, pad)
    if (core != null) {
      core := uIOCell.out
    }
    uIOCell.in := out
    if (uIOCell.hasOutputEnable) {
      uIOCell.oe.get := outEnable
    }
    if (uIOCell.hasPullEnable) {
      uIOCell.pe.get := pullEnable
    }
    if (uIOCell.hasInputEnable) {
      uIOCell.ie.get := inEnable
    }
    uIOCell
  }

  /** Instantiate an digital tri-state I/O pad, and connect it. */
  def createInoutIOCell[T <: Data](cell: => TriStateIOCell, portName: String)(
    core: T,
    out: T,
    outEnable: Bool = null,
    pullEnable: Bool = null,
    inEnable: Bool = null
  ): IndexedSeq[TriStateIOCell] = {
    val coreVec = Wire(VecInit(core.asUInt.asBools).cloneType)
    core := coreVec.asUInt.asTypeOf(core)
    for (i <- 0 until core.getWidth) yield {
      val name   = if (core.getWidth == 1) portName else portName + f"_$i"
      val pad    = IO(Analog(1.W)).suggestName(name)
      val iocell = connectTriStateIOCell(cell)(pad, coreVec(i), out.asUInt(i), outEnable, pullEnable, inEnable)
      iocell.suggestName(name + "_iocell")
    }
  }

  /** Instantiate an digital tri-state I/O pad, and connect it as an input-only pad. */
  def createInputIOCell[T <: Data](
    cell: => TriStateIOCell,
    portName: String
  )(core: T, pullEnable: Bool = null, inEnable: Bool = null): IndexedSeq[TriStateIOCell] = {
    val outOnes = VecInit(Seq.fill(core.getWidth)(true.B)).asTypeOf(core)
    createInoutIOCell(cell, portName)(core, outOnes, true.B, pullEnable, inEnable)
  }

  /** Instantiate an digital tri-state I/O pad, and connect it as an output-only pad. */
  def createOutputIOCell[T <: Data](
    cell: => TriStateIOCell,
    portName: String
  )(out: T, outEnable: Bool = null, pullEnable: Bool = null): IndexedSeq[TriStateIOCell] = {
    for (i <- 0 until out.getWidth) yield {
      val name   = if (out.getWidth == 1) portName else portName + f"_$i"
      val pad    = IO(Analog(1.W)).suggestName(name)
      val iocell = connectTriStateIOCell(cell)(pad, null, out.asUInt(i), outEnable, pullEnable, true.B)
      iocell.suggestName(name + "_iocell")
    }
  }
}
