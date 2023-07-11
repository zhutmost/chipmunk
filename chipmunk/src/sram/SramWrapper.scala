package chipmunk
package sram

import chisel3._
import chisel3.util._

import scala.io.Source

/** Some helper functions for SRAM macro wrappers. */
private[sram] object SramWrapper {
  def priorityInverse(p: SramPortPriority.Value): String = p match {
    case SramPortPriority.High => ""
    case SramPortPriority.Low  => "~ "
  }

  def extraPortBulkConnect(ps: List[SramExtraPort]): String = {
    val newLineBreak = "\n|        "
    ps.map(p => {
      p.direction match {
        case SramExtraPortDirection.In  => f".${p.name} (${p.constant}),"
        case SramExtraPortDirection.Out => f".${p.name} (/*Unconnected*/),"
      }
    }).mkString(newLineBreak)
  }

  def instancePatternReplace(path: String, instance: String, sramConfig: SramConfig): String = {
    Source
      .fromResource(path)
      .mkString
      .replaceAll("\\$NAME", s"${sramConfig.name}")
      .replaceAll("\\$DEPTH", s"${sramConfig.depth}")
      .replaceAll("\\$DATA_WIDTH", s"${sramConfig.depth}")
      .replaceAll("\\$ADDR_WIDTH", s"${sramConfig.addrWidth}")
      .replaceAll("\\$MASK_WIDTH", s"${sramConfig.maskWidth}")
      .replaceAll("\\$MASK_UNIT", s"${sramConfig.maskUnit}")
      .replaceAll("\\$SRAM_MACRO_INSTANCE", instance)
  }
}
import sram.SramWrapper._

private[sram] class Sram1rwWrapper(c: SramConfig) extends BlackBox with HasBlackBoxInline {
  require(c.mc.family == SramFamily.Ram1rw)
  override val desiredName = f"Sram1rwWrapper_${c.name}"

  val io = IO(new Bundle {
    val rw_clock = Input(Clock())
    val rw       = Slave(new SramReadWriteIO(c))
  })

  val mc = c.mc
  val maskLine: String = if (c.hasMask) {
    s".${mc.ports(0).mask.name} (${priorityInverse(mc.ports(0).mask.priority)}mem_rw_mask),"
  } else {
    ""
  }
  val instance: String =
    s"""
       |    ${c.name} uMem (
       |        ${extraPortBulkConnect(mc.extraPorts)}
       |        .${mc.ports(0).clock.name} (${priorityInverse(mc.ports(0).clock.priority)}mem_rw_clock),
       |        .${mc.ports(0).address.name} (${priorityInverse(mc.ports(0).address.priority)}mem_rw_addr),
       |        .${mc.ports(0).enable.name} (${priorityInverse(mc.ports(0).enable.priority)}mem_rw_enable),
       |        .${mc.ports(0).write.name} (${priorityInverse(mc.ports(0).write.priority)}mem_rw_write),
       |        .${mc.ports(0).input.name} (${priorityInverse(mc.ports(0).input.priority)}mem_rw_dataIn),
       |        $maskLine
       |        .${mc.ports(0).output.name} (mem_rw_dataOut)
       |    );
       |    assign rw_dataOut = ${priorityInverse(mc.ports(0).output.priority)}mem_rw_dataOut;
       |""".stripMargin

  val templatePath = if (c.hasMask) "sram/Sram1rw.template.sv" else "sram/Sram1rwMask.template.sv"
  val template     = instancePatternReplace(templatePath, instance, c)

  setInline(desiredName + ".sv", template)
}

private[sram] class Sram1r1wWrapper(c: SramConfig) extends BlackBox with HasBlackBoxInline {
  require(c.mc.family == SramFamily.Ram1r1w)
  override val desiredName = f"Sram1r1wWrapper_${c.name}"

  val io = IO(new Bundle {
    val wr_clock = Input(Clock())
    val wr       = Slave(new SramWriteIO(c))

    val rd_clock = Input(Clock())
    val rd       = Slave(new SramReadIO(c))
  })

  val mc = c.mc
  val maskLine: String = if (c.hasMask) {
    s".${mc.ports(0).mask.name} (${priorityInverse(mc.ports(0).mask.priority)}mem_wr_mask),"
  } else {
    ""
  }
  val instance: String =
    s"""
       |    ${c.name} uMem (
       |        ${extraPortBulkConnect(mc.extraPorts)}
       |        .${mc.ports(0).clock.name} (${priorityInverse(mc.ports(0).clock.priority)}mem_wr_clock),
       |        .${mc.ports(0).address.name} (${priorityInverse(mc.ports(0).address.priority)}mem_wr_addr),
       |        .${mc.ports(0).input.name} (${priorityInverse(mc.ports(0).input.priority)}mem_wr_dataIn),
       |        .${mc.ports(0).enable.name} (${priorityInverse(mc.ports(0).enable.priority)}mem_wr_enable),
       |        $maskLine
       |        .${mc.ports(1).clock.name} (${priorityInverse(mc.ports(1).clock.priority)}mem_rd_clock),
       |        .${mc.ports(1).address.name} (${priorityInverse(mc.ports(1).address.priority)}mem_rd_addr),
       |        .${mc.ports(1).enable.name} (${priorityInverse(mc.ports(1).enable.priority)}mem_rd_enable),
       |        .${mc.ports(1).output.name} (mem_rd_dataOut)
       |    );
       |    assign rd_dataOut = ${priorityInverse(mc.ports(1).output.priority)}mem_rd_dataOut;
       |""".stripMargin

  val templatePath = if (c.hasMask) "sram/Sram1r1w.template.sv" else "sram/Sram1r1wMask.template.sv"
  val template     = instancePatternReplace(templatePath, instance, c)

  setInline(desiredName + ".sv", template)
}

private[sram] class Sram2rwWrapper(c: SramConfig) extends BlackBox with HasBlackBoxInline {
  require(c.mc.family == SramFamily.Ram2rw)
  override val desiredName = f"Sram2rwWrapper_${c.name}"

  val io = IO(new Bundle {
    val rw0_clock = Input(Clock())
    val rw1_clock = Input(Clock())
    val rw0       = Slave(new SramReadWriteIO(c))
    val rw1       = Slave(new SramReadWriteIO(c))
  })

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
       |        ${extraPortBulkConnect(mc.extraPorts)}
       |        .${mc.ports(0).clock.name} (${priorityInverse(mc.ports(0).clock.priority)}mem_rw0_clock),
       |        .${mc.ports(0).address.name} (${priorityInverse(mc.ports(0).address.priority)}mem_rw0_addr),
       |        .${mc.ports(0).enable.name} (${priorityInverse(mc.ports(0).enable.priority)}mem_rw0_enable),
       |        .${mc.ports(0).write.name} (${priorityInverse(mc.ports(0).write.priority)}mem_rw0_write),
       |        .${mc.ports(0).input.name} (${priorityInverse(mc.ports(0).input.priority)}mem_rw0_dataIn),
       |        $maskLine0
       |        .${mc.ports(0).output.name} (mem_rw0_dataOut),
       |
       |        .${mc.ports(1).clock.name} (${priorityInverse(mc.ports(1).clock.priority)}mem_rw1_clock),
       |        .${mc.ports(1).address.name} (${priorityInverse(mc.ports(1).address.priority)}mem_rw1_addr),
       |        .${mc.ports(1).enable.name} (${priorityInverse(mc.ports(1).enable.priority)}mem_rw1_enable),
       |        .${mc.ports(1).write.name} (${priorityInverse(mc.ports(1).write.priority)}mem_rw1_write),
       |        .${mc.ports(1).input.name} (${priorityInverse(mc.ports(1).input.priority)}mem_rw1_dataIn),
       |        $maskLine1
       |        .${mc.ports(1).output.name} (mem_rw1_dataOut),
       |    );
       |    assign rw0_dataOut = ${priorityInverse(mc.ports(0).output.priority)}mem_rw0_dataOut;
       |    assign rw1_dataOut = ${priorityInverse(mc.ports(1).output.priority)}mem_rw1_dataOut;
       |""".stripMargin

  val templatePath = if (c.hasMask) "sram/Sram1rw.template.sv" else "sram/Sram1rwMask.template.sv"
  val template     = instancePatternReplace(templatePath, instance, c)

  setInline(desiredName + ".sv", template)
}
