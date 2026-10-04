package chipmunk
package acorn

import chisel3.*
import chisel3.util.Queue

/** Accept commands and return ordered error responses. Read errors return zero data.
  *
  * Read and write have independent response capacities. Responses remain stable under backpressure.
  */
class AcornErrorPoint(params: AcornParams, outstanding: AcornOutstanding = AcornOutstanding()) extends Module {
  val io = IO(new Bundle {
    val access = Slave(new AcornIO(params))
  })
  // Only response occupancy is stored; all error payloads are constant.
  private val reads = Module(new Queue(Bool(), outstanding.read, pipe = false, flow = false))
  reads.io.enq.valid          := io.access.rd.cmd.valid
  reads.io.enq.bits           := false.B
  io.access.rd.cmd.ready      := reads.io.enq.ready
  io.access.rd.rsp.valid      := reads.io.deq.valid
  io.access.rd.rsp.bits.data  := 0.U
  io.access.rd.rsp.bits.error := true.B
  reads.io.deq.ready          := io.access.rd.rsp.ready

  private val writes = Module(new Queue(Bool(), outstanding.write, pipe = false, flow = false))
  writes.io.enq.valid         := io.access.wr.cmd.valid
  writes.io.enq.bits          := false.B
  io.access.wr.cmd.ready      := writes.io.enq.ready
  io.access.wr.rsp.valid      := writes.io.deq.valid
  io.access.wr.rsp.bits.error := true.B
  writes.io.deq.ready         := io.access.wr.rsp.ready
}
