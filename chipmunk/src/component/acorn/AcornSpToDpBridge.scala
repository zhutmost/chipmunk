package chipmunk
package component.acorn
import stream._

import chisel3._

class AcornSpToDpBridge(dataWidth: Int = 32, addrWidth: Int = 32, outstanding: Int = 16) extends Module {
  val io = IO(new Bundle {
    val sAcornS = Slave(new AcornSpIO(dataWidth, addrWidth))
    val mAcornD = Master(new AcornDpIO(dataWidth, addrWidth))
  })

  // ---------------------------------------------------------------------------
  // Command channel

  val cmdDemux   = StreamDemux(in = io.sAcornS.cmd, select = io.sAcornS.cmd.bits.read.asUInt, num = 2)
  val cmdDemuxWr = cmdDemux(0)
  val cmdDemuxRd = cmdDemux(1)

  io.mAcornD.wr.cmd handshakeFrom cmdDemuxWr
  io.mAcornD.wr.cmd.bits.addr  := cmdDemuxWr.bits.addr
  io.mAcornD.wr.cmd.bits.wdata := cmdDemuxWr.bits.wdata
  io.mAcornD.wr.cmd.bits.wmask := cmdDemuxWr.bits.wmask

  io.mAcornD.rd.cmd handshakeFrom cmdDemuxRd
  io.mAcornD.rd.cmd.bits.addr := cmdDemuxRd.bits.addr

  // ---------------------------------------------------------------------------
  // Select generation

  val selectPush: StreamIO[UInt] = Wire(Stream(io.sAcornS.cmd.bits.read.asUInt))
  selectPush.bits  := io.sAcornS.cmd.bits.read
  selectPush.valid := io.sAcornS.cmd.fire

  val selectPop: StreamIO[UInt] = StreamQueue(enq = selectPush, entries = outstanding, pipe = true, flow = true)

  // ---------------------------------------------------------------------------
  // Response channel

  val respMuxWr = Wire(Stream(io.sAcornS.resp.bits))
  val respMuxRd = Wire(Stream(io.sAcornS.resp.bits))
  val respMux   = StreamMux(select = selectPop, ins = VecInit(respMuxWr, respMuxRd))
  respMuxWr.bits.rdata := 0.U
  respMuxWr.bits.error := io.mAcornD.wr.resp.bits.error
  respMuxRd.bits.rdata := io.mAcornD.rd.resp.bits.rdata
  respMuxRd.bits.error := io.mAcornD.rd.resp.bits.error

  io.sAcornS.resp << respMux
}
