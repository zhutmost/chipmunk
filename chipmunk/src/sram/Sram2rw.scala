package chipmunk
package sram

import chisel3._
import chisel3.util._

/** SRAM with dual read/write ports. The two ports have independent clocks.
  *
  * @param c
  *   [[SramConfig]].
  */
class Sram2rw(c: SramConfig) extends RawModule {
  require(c.mc.family == "ram2rw")

  val io = IO(new Bundle {
    val clock0 = Input(Clock())
    val clock1 = Input(Clock())
    val rw0    = Slave(new SramReadWriteIO(c))
    val rw1    = Slave(new SramReadWriteIO(c))
  })

  val uSram = Module(new Sram2rwWrapper(c))

  uSram.io.rw0_clock := io.clock0
  uSram.io.rw1_clock := io.clock1
  uSram.io.rw0 <> io.rw0
  uSram.io.rw1 <> io.rw1
}

class Sram2rwWrapper(c: SramConfig) extends BlackBox with HasBlackBoxInline {
  require(c.mc.family == "ram2rw")
  override val desiredName = c.name

  val io = IO(new Bundle {
    val rw0_clock = Input(Clock())
    val rw1_clock = Input(Clock())
    val rw0       = Slave(new SramReadWriteIO(c))
    val rw1       = Slave(new SramReadWriteIO(c))
  })

  import SramUtils._

  val mc = c.mc
  val maskLine0: String = if (c.hasMask) {
    s".${mc.ports(0).mask.name} (${priorityInverse(mc.ports(0).mask.priority)}mem_rw0_mask),"
  } else {
    ""
  }
  val maskLine1: String = if (mc.hasMask) {
    s".${mc.ports(1).mask.name} (${priorityInverse(mc.ports(1).mask.priority)}mem_rw1_mask),"
  } else {
    ""
  }
  val instance: String =
    s"""
       |    ${c.name} uMem (
       |        .${mc.ports(0).clock.name}  (${priorityInverse(mc.ports(0).clock.priority)}mem_rw0_clock),
       |        .${mc.ports(0).address.name}(${priorityInverse(mc.ports(0).address.priority)}mem_rw0_addr),
       |        .${mc.ports(0).enable.name} (${priorityInverse(mc.ports(0).enable.priority)}mem_rw0_enable),
       |        .${mc.ports(0).write.name} (${priorityInverse(mc.ports(0).write.priority)}mem_rw0_write),
       |        .${mc.ports(0).input.name}  (${priorityInverse(mc.ports(0).input.priority)}mem_rw0_dataIn),
       |        $maskLine0
       |        .${mc.ports(0).output.name} (mem_rw0_dataOut),
       |
       |        .${mc.ports(1).clock.name}  (${priorityInverse(mc.ports(1).clock.priority)}mem_rw1_clock),
       |        .${mc.ports(1).address.name}(${priorityInverse(mc.ports(1).address.priority)}mem_rw1_addr),
       |        .${mc.ports(1).enable.name} (${priorityInverse(mc.ports(1).enable.priority)}mem_rw1_enable),
       |        .${mc.ports(1).write.name} (${priorityInverse(mc.ports(1).write.priority)}mem_rw1_write),
       |        .${mc.ports(1).input.name}  (${priorityInverse(mc.ports(1).input.priority)}mem_rw1_dataIn),
       |        $maskLine1
       |        .${mc.ports(1).output.name} (mem_rw1_dataOut),
       |    );
       |    assign rw0_dataOut = ${priorityInverse(mc.ports(0).output.priority)}mem_rw0_dataOut;
       |    assign rw1_dataOut = ${priorityInverse(mc.ports(1).output.priority)}mem_rw1_dataOut;
       |""".stripMargin

  val templatePath = if (c.hasMask) "sram/Sram1rw.template.sv" else "sram/Sram1rwMask.template.sv"
  val template     = templatePatternReplace(templatePath, instance, c)

  setInline(desiredName + ".sv", template)
}
