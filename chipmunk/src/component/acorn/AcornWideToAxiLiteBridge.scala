package chipmunk
package component.acorn

import amba._

import chisel3._
import chisel3.util.Fill

class AcornWideToAxiLiteBridge(addrWidth: Int = 32, maskUnit: Int = 8) extends Module {
  val dataWidth: Int   = 32
  val statusWidth: Int = AxiResp.getWidth
  val io = IO(new Bundle {
    val sAcornW = Slave(new AcornWideIO(addrWidth, dataWidth, maskUnit))
    val mAxiL   = Master(new AxiLiteIO(dataWidth, addrWidth))
  })

  // ---------------------------------------------------------------------------
  // Write (aw, w & b)

  val waitWriteChannel = RegInit(false.B)
  when(io.mAxiL.aw.fire && !io.mAxiL.w.fire) {
    waitWriteChannel := true.B
  }.elsewhen(io.mAxiL.w.fire) {
    waitWriteChannel := false.B
  }

  io.mAxiL.aw.valid := io.sAcornW.wr.cmd.valid && !waitWriteChannel

  io.sAcornW.wr.cmd.ready := io.mAxiL.w.fire && (waitWriteChannel || io.mAxiL.aw.fire)
  io.mAxiL.w.valid        := io.sAcornW.wr.cmd.valid

  io.mAxiL.aw.bits.addr := io.sAcornW.wr.cmd.bits.addr
  io.mAxiL.aw.bits.prot := 0.U

  io.mAxiL.w.bits.data := io.sAcornW.wr.cmd.bits.wdata
  if (io.sAcornW.hasMask) {
    io.mAxiL.w.bits.strb := io.sAcornW.wr.cmd.bits.wmask.get
  } else {
    io.mAxiL.w.bits.strb := Fill(io.mAxiL.w.bits.strobeWidth, true.B)
  }

  io.sAcornW.wr.resp handshakeFrom io.mAxiL.b
  io.sAcornW.wr.resp.bits.status := io.mAxiL.b.bits.resp.asUInt

  // ---------------------------------------------------------------------------
  // Read (ar & r)

  io.mAxiL.ar handshakeFrom io.sAcornW.rd.cmd
  io.mAxiL.ar.bits.addr := io.sAcornW.rd.cmd.bits.addr
  io.mAxiL.ar.bits.prot := 0.U

  io.sAcornW.rd.resp handshakeFrom io.mAxiL.r
  io.sAcornW.rd.resp.bits.rdata  := io.mAxiL.r.bits.data
  io.sAcornW.rd.resp.bits.status := io.mAxiL.r.bits.resp.asUInt
}
