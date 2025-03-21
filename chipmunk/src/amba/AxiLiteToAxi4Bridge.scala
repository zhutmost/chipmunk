package chipmunk
package amba

import chisel3._
import chisel3.util._

class AxiLiteToAxi4Bridge(dataWidth: Int, addrWidth: Int, idWidth: Int, writeId: Int = 0, readId: Int = 0)
    extends Module {
  val io = IO(new Bundle {
    val sAxiL = Slave(new Axi4LiteIO(dataWidth, addrWidth))
    val mAxi4 = Master(new Axi4IO(dataWidth, addrWidth, idWidth))
  })

  io.mAxi4.aw handshakeFrom io.sAxiL.aw
  io.mAxi4.aw.bits.addr  := io.sAxiL.aw.bits.addr
  io.mAxi4.aw.bits.prot  := io.sAxiL.aw.bits.prot
  io.mAxi4.aw.bits.size  := AxiBurstSize(log2Ceil(io.sAxiL.strobeWidth).U)
  io.mAxi4.aw.bits.len   := 0.U
  io.mAxi4.aw.bits.burst := AxiBurstType.BURST_INCR
  io.mAxi4.aw.bits.lock  := 0.U
  io.mAxi4.aw.bits.cache := 3.U // Recommended by Xilinx UG1037
  io.mAxi4.aw.bits.qos.foreach(_ := 0.U)
  io.mAxi4.aw.bits.region.foreach(_ := 0.U)
  io.mAxi4.aw.bits.id.foreach(_ := writeId.U)

  io.mAxi4.ar handshakeFrom io.sAxiL.ar
  io.mAxi4.ar.bits.addr  := io.sAxiL.ar.bits.addr
  io.mAxi4.ar.bits.prot  := io.sAxiL.ar.bits.prot
  io.mAxi4.ar.bits.size  := AxiBurstSize(log2Ceil(io.sAxiL.strobeWidth).U)
  io.mAxi4.ar.bits.len   := 0.U
  io.mAxi4.ar.bits.burst := AxiBurstType.BURST_INCR
  io.mAxi4.ar.bits.lock  := 0.U
  io.mAxi4.ar.bits.cache := 3.U // Recommended by Xilinx UG1037
  io.mAxi4.ar.bits.qos.foreach(_ := 0.U)
  io.mAxi4.ar.bits.region.foreach(_ := 0.U)
  io.mAxi4.ar.bits.id.foreach(_ := readId.U)

  io.mAxi4.w handshakeFrom io.sAxiL.w
  io.mAxi4.w.bits.data := io.sAxiL.w.bits.data
  io.mAxi4.w.bits.strb := io.sAxiL.w.bits.strb
  io.mAxi4.w.bits.last := true.B

  io.sAxiL.r handshakeFrom io.mAxi4.r
  io.sAxiL.r.bits.data := io.mAxi4.r.bits.data
  io.sAxiL.r.bits.resp := io.mAxi4.r.bits.resp

  io.sAxiL.b handshakeFrom io.mAxi4.b
  io.sAxiL.b.bits.resp := io.mAxi4.b.bits.resp
}
