package chipmunk
package sram

import chisel3._
import chisel3.util._

/** SRAM with one read-only port and one write-only port. The two ports have independent clocks.
  *
  * @param c
  *   [[SramConfig]].
  */
class Sram1r1w(c: SramConfig) extends RawModule {
  require(c.mc.family != "ram1rw")

  val io = IO(new Bundle {
    val clockR = Input(Clock())
    val clockW = Input(Clock())
    val r      = Slave(new SramReadIO(c))
    val w      = Slave(new SramWriteIO(c))
  })

  if (c.mc.family == "ram2rw") {
    val uSram = Module(new Sram2rwWrapper(c))
    uSram.io.rw0_clock   := io.clockW
    uSram.io.rw1_clock   := io.clockR
    uSram.io.rw0.enable  := io.w.enable
    uSram.io.rw0.address := io.w.address
    uSram.io.rw0.dataIn  := io.w.dataIn
    uSram.io.rw0.write   := true.B
    uSram.io.rw1.enable  := io.r.enable
    uSram.io.rw1.address := io.r.address
    uSram.io.rw1.write   := false.B
    uSram.io.rw1.dataIn  := 0.U
    io.r.dataOut         := uSram.io.rw1.dataOut
    if (c.hasMask) {
      uSram.io.rw0.mask.get := io.w.mask.get
      uSram.io.rw1.mask.get := 0.U
    }
  } else {
    val uSram = Module(new Sram1r1wWrapper(c))
    uSram.io.wr_clock := io.clockW
    uSram.io.rd_clock := io.clockR
    uSram.io.wr <> io.w
    uSram.io.rd <> io.r
  }
}

class Sram1r1wWrapper(c: SramConfig) extends BlackBox with HasBlackBoxInline {
  require(c.mc.family == "ram1r1w")
  override val desiredName = "Sram1r1wWrapper_" + c.name

  val io = IO(new Bundle {
    val wr_clock = Input(Clock())
    val wr       = Slave(new SramWriteIO(c))

    val rd_clock = Input(Clock())
    val rd       = Slave(new SramReadIO(c))
  })

  import SramUtils._

  val mc = c.mc
  val maskLine: String = if (c.hasMask) {
    s".${mc.ports(0).mask.name} (${priorityInverse(mc.ports(0).mask.priority)}mem_wr_mask),"
  } else {
    ""
  }
  val instance: String =
    s"""
       |    ${c.name} uMem (
       |        .${mc.ports(0).clock.name}  (${priorityInverse(mc.ports(0).clock.priority)}mem_wr_clock),
       |        .${mc.ports(0).address.name}(${priorityInverse(mc.ports(0).address.priority)}mem_wr_addr),
       |        .${mc.ports(0).input.name}  (${priorityInverse(mc.ports(0).input.priority)}mem_wr_dataIn),
       |        .${mc.ports(0).enable.name} (${priorityInverse(mc.ports(0).enable.priority)}mem_wr_enable),
       |        ${maskLine}
       |        .${mc.ports(1).clock.name}  (${priorityInverse(mc.ports(1).clock.priority)}mem_rd_clock),
       |        .${mc.ports(1).address.name}(${priorityInverse(mc.ports(1).address.priority)}mem_rd_addr),
       |        .${mc.ports(1).enable.name} (${priorityInverse(mc.ports(1).enable.priority)}mem_rd_enable),
       |        .${mc.ports(1).output.name} (mem_rd_dataOut)
       |    );
       |    assign rd_dataOut = ${priorityInverse(mc.ports(1).output.priority)}mem_rd_dataOut;
       |""".stripMargin

  val templatePath = if (c.hasMask) "sram/Sram1r1w.template.sv" else "sram/Sram1r1wMask.template.sv"
  val template     = templatePatternReplace(templatePath, instance, c)

  setInline(desiredName + ".sv", template)
}
