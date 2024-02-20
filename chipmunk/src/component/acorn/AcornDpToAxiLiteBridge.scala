package chipmunk
package component.acorn

import amba._

import chisel3._

class AcornDpToAxiLiteBridge(dataWidth: Int = 32, addrWidth: Int = 32) extends Module {
  val statusWidth: Int = AxiResp.getWidth
  val io = IO(new Bundle {
    val sAcornD = Slave(new AcornDpIO(dataWidth, addrWidth))
    val mAxiL   = Master(new Axi4LiteIO(dataWidth, addrWidth))
  })

  // ---------------------------------------------------------------------------
  // Write (aw, w & b)

  val waitWriteChannel = RegInit(false.B)
  when(io.mAxiL.aw.fire && !io.mAxiL.w.fire) {
    waitWriteChannel := true.B
  }.elsewhen(io.mAxiL.w.fire) {
    waitWriteChannel := false.B
  }

  io.mAxiL.aw.valid := io.sAcornD.wr.cmd.valid && !waitWriteChannel

  io.sAcornD.wr.cmd.ready := io.mAxiL.w.fire && (waitWriteChannel || io.mAxiL.aw.fire)
  io.mAxiL.w.valid        := io.sAcornD.wr.cmd.valid

  io.mAxiL.aw.bits.addr := io.sAcornD.wr.cmd.bits.addr
  io.mAxiL.aw.bits.prot := 0.U

  io.mAxiL.w.bits.data := io.sAcornD.wr.cmd.bits.wdata
  io.mAxiL.w.bits.strb := io.sAcornD.wr.cmd.bits.wmask

  io.sAcornD.wr.resp handshakeFrom io.mAxiL.b
  io.sAcornD.wr.resp.bits.status := io.mAxiL.b.bits.resp =/= AxiResp.RESP_OKAY

  // ---------------------------------------------------------------------------
  // Read (ar & r)

  io.mAxiL.ar handshakeFrom io.sAcornD.rd.cmd
  io.mAxiL.ar.bits.addr := io.sAcornD.rd.cmd.bits.addr
  io.mAxiL.ar.bits.prot := 0.U

  io.sAcornD.rd.resp handshakeFrom io.mAxiL.r
  io.sAcornD.rd.resp.bits.rdata  := io.mAxiL.r.bits.data
  io.sAcornD.rd.resp.bits.status := io.mAxiL.r.bits.resp =/= AxiResp.RESP_OKAY
}
