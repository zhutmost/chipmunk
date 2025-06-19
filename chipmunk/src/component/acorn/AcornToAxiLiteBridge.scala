package chipmunk
package component.acorn

import amba._

import chisel3._

class AcornToAxiLiteBridge(dataWidth: Int = 32, addrWidth: Int = 32) extends Module {
  val io = IO(new Bundle {
    val sAcorn = Slave(new AcornIO(dataWidth, addrWidth))
    val mAxiL  = Master(new Axi4LiteIO(dataWidth, addrWidth))
  })

  // ---------------------------------------------------------------------------
  // Write (aw, w & b)

  val waitWriteChannel = RegInit(false.B)
  when(io.mAxiL.aw.fire && !io.mAxiL.w.fire) {
    waitWriteChannel := true.B
  }.elsewhen(io.mAxiL.w.fire) {
    waitWriteChannel := false.B
  }

  io.mAxiL.aw.valid := io.sAcorn.wr.cmd.valid && !waitWriteChannel

  io.sAcorn.wr.cmd.ready := io.mAxiL.w.fire && (waitWriteChannel || io.mAxiL.aw.fire)
  io.mAxiL.w.valid       := io.sAcorn.wr.cmd.valid

  io.mAxiL.aw.bits.addr := io.sAcorn.wr.cmd.bits.addr
  io.mAxiL.aw.bits.prot := 0.U

  io.mAxiL.w.bits.data := io.sAcorn.wr.cmd.bits.data
  io.mAxiL.w.bits.strb := io.sAcorn.wr.cmd.bits.strobe

  io.sAcorn.wr.rsp handshakeFrom io.mAxiL.b
  io.sAcorn.wr.rsp.bits.error := io.mAxiL.b.bits.resp =/= AxiResp.RESP_OKAY

  // ---------------------------------------------------------------------------
  // Read (ar & r)

  io.mAxiL.ar handshakeFrom io.sAcorn.rd.cmd
  io.mAxiL.ar.bits.addr := io.sAcorn.rd.cmd.bits.addr
  io.mAxiL.ar.bits.prot := 0.U

  io.sAcorn.rd.rsp handshakeFrom io.mAxiL.r
  io.sAcorn.rd.rsp.bits.data  := io.mAxiL.r.bits.data
  io.sAcorn.rd.rsp.bits.error := io.mAxiL.r.bits.resp =/= AxiResp.RESP_OKAY
}
