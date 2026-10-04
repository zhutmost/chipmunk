package chipmunk
package acorn

import chisel3.*

/** Merge masters into one slave. Capacities are aggregate across all masters, separately for reads and writes. */
class AcornMux(
  params: AcornParams,
  numMasters: Int,
  outstanding: AcornOutstanding = AcornOutstanding(),
  arbitration: AcornArbitration = AcornArbitration.RoundRobin,
) extends Module {
  require(numMasters >= 1, "AcornMux needs at least one master.")
  val io = IO(new Bundle {
    val ins = Vec(numMasters, Slave(new AcornIO(params)))
    val out = Master(new AcornIO(params))
  })

  AcornRouting.merge(
    io.ins.map(_.rd.cmd),
    io.out.rd.cmd,
    io.out.rd.rsp,
    io.ins.map(_.rd.rsp),
    outstanding.read,
    arbitration,
  )
  AcornRouting.merge(
    io.ins.map(_.wr.cmd),
    io.out.wr.cmd,
    io.out.wr.rsp,
    io.ins.map(_.wr.rsp),
    outstanding.write,
    arbitration,
  )
}
