package chipmunk
package amba

import chisel3.*

import chipmunk.acorn.{AcornIO, AcornParams}

/** Convert Acorn word accesses to same-width AXI4-Lite transactions.
  *
  * Reuses [[AcornToAxiBridge]] transaction control with no IDs and projects its single-beat channels onto AXI4-Lite.
  * Each direction retains one command until its Acorn response transfers. AW/W are independent, responses are buffered,
  * and unaligned commands return local errors. Field projection adds no buffering or latency.
  */
final class AcornToAxiLiteBridge(val params: AxiLiteParams, writeProt: Int = 0, readProt: Int = 0) extends Module {
  val io = IO(new Bundle {
    val sAcorn   = Slave(new AcornIO(AcornParams(params.dataWidth, params.addrWidth)))
    val mAxiLite = Master(new AxiLiteIO(params))
  })

  private val bridge = Module(
    new AcornToAxiBridge(AxiParams(params.dataWidth, params.addrWidth), writeProt = writeProt, readProt = readProt)
  )
  bridge.io.sAcorn :<>= io.sAcorn

  io.mAxiLite.aw handshakeFrom bridge.io.mAxi.aw
  io.mAxiLite.aw.bits.addr := bridge.io.mAxi.aw.bits.addr
  io.mAxiLite.aw.bits.prot := bridge.io.mAxi.aw.bits.prot

  io.mAxiLite.w handshakeFrom bridge.io.mAxi.w
  io.mAxiLite.w.bits.data := bridge.io.mAxi.w.bits.data
  io.mAxiLite.w.bits.strb := bridge.io.mAxi.w.bits.strb

  bridge.io.mAxi.b handshakeFrom io.mAxiLite.b
  bridge.io.mAxi.b.bits.resp := io.mAxiLite.b.bits.resp

  io.mAxiLite.ar handshakeFrom bridge.io.mAxi.ar
  io.mAxiLite.ar.bits.addr := bridge.io.mAxi.ar.bits.addr
  io.mAxiLite.ar.bits.prot := bridge.io.mAxi.ar.bits.prot

  bridge.io.mAxi.r handshakeFrom io.mAxiLite.r
  bridge.io.mAxi.r.bits.data := io.mAxiLite.r.bits.data
  bridge.io.mAxi.r.bits.resp := io.mAxiLite.r.bits.resp
  bridge.io.mAxi.r.bits.last := true.B
}
