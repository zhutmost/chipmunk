package chipmunk
package component.acorn

import stream._

import chisel3._
import chisel3.util._

class AcornDpToSpBridge(dataWidth: Int = 32, addrWidth: Int = 32, outstanding: Int = 16) extends Module {
  val io = IO(new Bundle {
    val sAcornD = Slave(new AcornDpIO(dataWidth, addrWidth))
    val mAcornS = Master(new AcornSpIO(dataWidth, addrWidth))
  })

  // ---------------------------------------------------------------------------
  // Command channel

  val cmdArbiterWr = Wire(Stream(io.mAcornS.cmd.bits))
  val cmdArbiterRd = Wire(Stream(io.mAcornS.cmd.bits))
  val cmdArbiter   = StreamArbiter.roundRobin(ins = Seq(cmdArbiterWr, cmdArbiterRd))

  cmdArbiterWr handshakeFrom io.sAcornD.wr.cmd
  cmdArbiterWr.bits.addr  := io.sAcornD.wr.cmd.bits.addr
  cmdArbiterWr.bits.read  := false.B
  cmdArbiterWr.bits.wdata := io.sAcornD.wr.cmd.bits.wdata
  cmdArbiterWr.bits.wmask := io.sAcornD.wr.cmd.bits.wmask

  cmdArbiterRd handshakeFrom io.sAcornD.rd.cmd
  cmdArbiterRd.bits.addr  := io.sAcornD.rd.cmd.bits.addr
  cmdArbiterRd.bits.read  := true.B
  cmdArbiterWr.bits.wdata := 0.U
  cmdArbiterWr.bits.wmask := Fill(io.mAcornS.cmd.bits.maskWidth, true.B)

  io.mAcornS.cmd << cmdArbiter

  // ---------------------------------------------------------------------------
  // Select generation

  val selectPush: StreamIO[UInt] = Wire(Stream(io.mAcornS.cmd.bits.read.asUInt))
  selectPush.bits  := io.mAcornS.cmd.bits.read
  selectPush.valid := io.mAcornS.cmd.fire

  val selectPop: StreamIO[UInt] = StreamQueue(enq = selectPush, entries = outstanding, pipe = true, flow = true)

  // ---------------------------------------------------------------------------
  // Response channel

  val respDemux   = StreamDemux(in = io.mAcornS.resp, select = selectPop, num = 2)
  val respDemuxWr = respDemux(0)
  val respDemuxRd = respDemux(1)

  io.sAcornD.wr.resp handshakeFrom respDemuxWr
  io.sAcornD.rd.resp.bits.status := respDemuxRd.bits.status
  io.sAcornD.rd.resp.bits.rdata  := respDemuxRd.bits.rdata

  io.sAcornD.rd.resp handshakeFrom respDemuxRd
  io.sAcornD.wr.resp.bits.status := respDemuxWr.bits.status
}
