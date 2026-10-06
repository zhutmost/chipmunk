package chipmunk
package amba

import chisel3.*

/** Adapt AXI4-Lite to same-width, single-beat AXI4 transactions with fixed IDs.
  *
  * All five channels are connected combinationally, without buffering or added latency. AW and W remain independent.
  * Transactions in each direction use one ID and retain their order.
  */
final class AxiLiteToAxiBridge(val params: AxiParams, writeId: BigInt = 0, readId: BigInt = 0) extends Module {
  private val liteParams = AxiLiteParams(params.dataWidth, params.addrWidth)

  require(writeId >= 0 && writeId.bitLength <= params.idWidth, "Write ID must fit the AXI ID width.")
  require(readId >= 0 && readId.bitLength <= params.idWidth, "Read ID must fit the AXI ID width.")

  val io = IO(new Bundle {
    val sAxiLite = Slave(new AxiLiteIO(liteParams))
    val mAxi     = Master(new AxiIO(params))
  })

  io.mAxi.aw handshakeFrom io.sAxiLite.aw
  driveAddress(io.mAxi.aw.bits, io.sAxiLite.aw.bits, writeId)

  io.mAxi.ar handshakeFrom io.sAxiLite.ar
  driveAddress(io.mAxi.ar.bits, io.sAxiLite.ar.bits, readId)

  io.mAxi.w handshakeFrom io.sAxiLite.w
  io.mAxi.w.bits.data := io.sAxiLite.w.bits.data
  io.mAxi.w.bits.strb := io.sAxiLite.w.bits.strb
  io.mAxi.w.bits.last := true.B

  io.sAxiLite.b handshakeFrom io.mAxi.b
  io.sAxiLite.b.bits.resp := io.mAxi.b.bits.resp

  io.sAxiLite.r handshakeFrom io.mAxi.r
  io.sAxiLite.r.bits.data := io.mAxi.r.bits.data
  io.sAxiLite.r.bits.resp := io.mAxi.r.bits.resp

  when(io.mAxi.b.fire) {
    io.mAxi.b.bits.id.foreach(id => assert(id === writeId.U, "Unexpected AXI write response ID."))
    assert(io.mAxi.b.bits.resp =/= AxiResp.RESP_EXOKAY, "Normal AXI writes must not return EXOKAY.")
  }

  when(io.mAxi.r.fire) {
    io.mAxi.r.bits.id.foreach(id => assert(id === readId.U, "Unexpected AXI read response ID."))
    assert(io.mAxi.r.bits.last, "Single-beat AXI reads must assert RLAST.")
    assert(io.mAxi.r.bits.resp =/= AxiResp.RESP_EXOKAY, "Normal AXI reads must not return EXOKAY.")
  }

  private def driveAddress(out: AxiWriteAddrChannel, in: AxiLiteAddrChannel, id: BigInt): Unit = {
    out.addr  := in.addr
    out.prot  := in.prot
    out.size  := AxiBurstSize(params.fullSize.U)
    out.len   := 0.U
    out.burst := AxiBurstType.BURST_INCR
    out.lock  := 0.U
    out.cache := 0.U
    out.id.foreach(_ := id.U(params.idWidth.W))
    out.qos.foreach(_ := 0.U)
    out.region.foreach(_ := 0.U)
  }
}
