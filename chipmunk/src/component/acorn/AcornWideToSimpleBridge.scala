package chipmunk
package component.acorn

import stream._

import chisel3.util._
import chisel3._

class AcornWideToSimpleBridge(dataWidth: Int = 32, addrWidth: Int = 32, maskUnit: Int = 0, outstanding: Int = 16)
    extends Module {
  val io = IO(new Bundle {
    val sAcornW = Slave(new AcornWideIO(addrWidth, dataWidth, maskUnit))
    val mAcornS = Master(new AcornSimpleIO(addrWidth, dataWidth, maskUnit))
  })

  // ---------------------------------------------------------------------------
  // Command channel

  val cmdArbiterWr = Wire(Stream(io.mAcornS.cmd.bits))
  val cmdArbiterRd = Wire(Stream(io.mAcornS.cmd.bits))
  val cmdArbiter   = StreamArbiter.roundRobin(ins = Seq(cmdArbiterWr, cmdArbiterRd))

  cmdArbiterWr handshakeFrom io.sAcornW.wr.cmd
  cmdArbiterWr.bits.addr  := io.sAcornW.wr.cmd.bits.addr
  cmdArbiterWr.bits.read  := false.B
  cmdArbiterWr.bits.wdata := io.sAcornW.wr.cmd.bits.wdata
  if (io.sAcornW.hasMask) {
    cmdArbiterWr.bits.wmask.get := io.sAcornW.wr.cmd.bits.wmask.get
  }

  cmdArbiterRd handshakeFrom io.sAcornW.rd.cmd
  cmdArbiterRd.bits.addr  := io.sAcornW.rd.cmd.bits.addr
  cmdArbiterRd.bits.read  := true.B
  cmdArbiterWr.bits.wdata := 0.U
  if (io.sAcornW.hasMask) {
    cmdArbiterWr.bits.wmask.get := Fill(io.sAcornW.maskWidth, true.B)
  }

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

  io.sAcornW.wr.resp handshakeFrom respDemuxWr
  io.sAcornW.rd.resp.bits.status := respDemuxRd.bits.status
  io.sAcornW.rd.resp.bits.rdata  := respDemuxRd.bits.rdata

  io.sAcornW.rd.resp handshakeFrom respDemuxRd
  io.sAcornW.wr.resp.bits.status := respDemuxWr.bits.status
}
