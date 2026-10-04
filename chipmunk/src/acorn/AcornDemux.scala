package chipmunk
package acorn

import chisel3.*
import chisel3.util.log2Ceil

/** Route a master to slaves, preserving its read and write response order across all destinations.
  *
  * rdSelect and wrSelect are independent selectors. An offered destination is held until its command transfers. Invalid
  * selectors stall; AcornCrossbar supplies a valid error-endpoint index for invalid addresses instead. This component
  * does not translate addresses. Do not insert independent command queues between it and AcornMux: cross-target issue
  * reordering can introduce cyclic response waits. Pipeline insertion needs a separate design.
  */
class AcornDemux(params: AcornParams, numSlaves: Int, outstanding: AcornOutstanding = AcornOutstanding())
    extends Module {
  require(numSlaves >= 1, "AcornDemux needs at least one slave.")
  val io = IO(new Bundle {
    val in       = Slave(new AcornIO(params))
    val outs     = Vec(numSlaves, Master(new AcornIO(params)))
    val rdSelect = Input(UInt(math.max(1, log2Ceil(numSlaves)).W))
    val wrSelect = Input(UInt(math.max(1, log2Ceil(numSlaves)).W))
  })

  AcornRouting.route(
    io.in.rd.cmd,
    io.outs.map(_.rd.cmd),
    io.rdSelect,
    io.outs.map(_.rd.rsp),
    io.in.rd.rsp,
    outstanding.read,
  )
  AcornRouting.route(
    io.in.wr.cmd,
    io.outs.map(_.wr.cmd),
    io.wrSelect,
    io.outs.map(_.wr.rsp),
    io.in.wr.rsp,
    outstanding.write,
  )
}
