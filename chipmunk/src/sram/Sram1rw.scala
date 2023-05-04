package chipmunk
package sram

import chisel3._
import chisel3.util._

/** SRAM with a single read/write port.
  *
  * @param c
  *   [[SramConfig]].
  */
class Sram1rw(c: SramConfig) extends RawModule {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val rw    = Slave(new SramReadWriteIO(c))
  })

  if (c.mc.family == "ram1rw") {
    val uSram = Module(new Sram1rwWrapper(c))
    uSram.io.rw_clock := io.clock
    uSram.io.rw <> io.rw
  } else if (c.mc.family == "ram1r1w") {
    val uSram = Module(new Sram1r1wWrapper(c))
    uSram.io.wr_clock   := io.clock
    uSram.io.rd_clock   := io.clock
    uSram.io.wr.address := io.rw.address
    uSram.io.wr.enable  := io.rw.enable && io.rw.write
    uSram.io.wr.dataIn  := io.rw.dataIn
    if (c.hasMask) {
      uSram.io.wr.mask.get := io.rw.mask.get
    }
    uSram.io.rd.address := io.rw.address
    uSram.io.rd.enable  := io.rw.enable && !io.rw.write
    io.rw.dataOut       := uSram.io.rd.dataOut
  } else if (c.mc.family == "ram2rw") {
    val uSram = Module(new Sram2rwWrapper(c))
    uSram.io.rw0_clock := io.clock
    uSram.io.rw1_clock := io.clock
    uSram.io.rw0 <> io.rw
    uSram.io.rw1.enable  := false.B
    uSram.io.rw1.write   := false.B
    uSram.io.rw1.address := 0.U
    uSram.io.rw1.dataIn  := 0.U
    if (c.hasMask) {
      uSram.io.rw1.mask.get := 0.U
    }
  }
}

class Sram1rwWrapper(c: SramConfig) extends BlackBox with HasBlackBoxInline {
  require(c.mc.family == "ram1rw")
  override val desiredName = c.name

  val io = IO(new Bundle {
    val rw_clock = Input(Clock())
    val rw       = Slave(new SramReadWriteIO(c))
  })

  import SramUtils._

  val mc = c.mc
  val maskLine: String = if (c.hasMask) {
    s".${mc.ports(0).mask.name} (${priorityInverse(mc.ports(0).mask.priority)}mem_rw_mask),"
  } else {
    ""
  }
  val instance: String =
    s"""
       |    ${c.name} uMem (
       |        .${mc.ports(0).clock.name}  (${priorityInverse(mc.ports(0).clock.priority)}mem_rw_clock),
       |        .${mc.ports(0).address.name}(${priorityInverse(mc.ports(0).address.priority)}mem_rw_addr),
       |        .${mc.ports(0).enable.name} (${priorityInverse(mc.ports(0).enable.priority)}mem_rw_enable),
       |        .${mc.ports(0).write.name} (${priorityInverse(mc.ports(0).write.priority)}mem_rw_write),
       |        .${mc.ports(0).input.name}  (${priorityInverse(mc.ports(0).input.priority)}mem_rw_dataIn),
       |        ${maskLine}
       |        .${mc.ports(0).output.name} (mem_rw_dataOut)
       |    );
       |    assign rw_dataOut = ${priorityInverse(mc.ports(1).output.priority)}mem_rw_dataOut;
       |""".stripMargin

  val templatePath = if (c.hasMask) "sram/Sram1rw.template.sv" else "sram/Sram1rwMask.template.sv"
  val template     = templatePatternReplace(templatePath, instance, c)

  setInline(desiredName + ".sv", template)
}
