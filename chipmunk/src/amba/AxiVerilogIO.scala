package chipmunk
package amba

import java.util.Locale

import chisel3.Record

/** Shared leaf naming for AXI and AXI-Lite. */
private[amba] trait HasAxiVerilogIO extends HasVerilogIO {
  this: Record =>

  override final def generatePortName(path: Seq[String]): String = {
    val channels = Set("aw", "w", "b", "ar", "r")
    val name     = path match {
      case Seq(channel, "bits", field) if channels.contains(channel) => channel + field
      case Seq(channel, signal) if channels.contains(channel) && (signal == "valid" || signal == "ready") =>
        channel + signal
      case _ => throw new IllegalArgumentException(s"Unsupported AXI RTL path: ${path.mkString(".")}")
    }
    name.toUpperCase(Locale.ROOT)
  }
}
