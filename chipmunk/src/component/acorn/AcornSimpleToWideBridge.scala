package chipmunk
package component.acorn
import chipmunk.stream._
import chisel3._
import chisel3.util._

class AcornSimpleToWideBridge(dataWidth: Int = 32, addrWidth: Int = 32, maskUnit: Int = 0, outstanding: Int = 16)
    extends Module {
  val io = IO(new Bundle {
    val sAcornS = Slave(new AcornSimpleIO(addrWidth, dataWidth, maskUnit))
    val mAcornW = Master(new AcornWideIO(addrWidth, dataWidth, maskUnit))
  })

  // ---------------------------------------------------------------------------
  // Command channel

  val cmdDemux   = StreamDemux(in = io.sAcornS.cmd, select = io.sAcornS.cmd.bits.read.asUInt, num = 2)
  val cmdDemuxWr = cmdDemux(0)
  val cmdDemuxRd = cmdDemux(1)

  io.mAcornW.wr.cmd handshakeFrom cmdDemuxWr
  io.mAcornW.wr.cmd.bits.addr  := cmdDemuxWr.bits.addr
  io.mAcornW.wr.cmd.bits.wdata := cmdDemuxWr.bits.wdata
  if (io.mAcornW.hasMask) {
    io.mAcornW.wr.cmd.bits.wmask.get := cmdDemuxWr.bits.wmask.get
  }

  io.mAcornW.rd.cmd handshakeFrom cmdDemuxRd
  io.mAcornW.rd.cmd.bits.addr := cmdDemuxRd.bits.addr

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
  respMuxWr.bits.rdata  := 0.U
  respMuxWr.bits.status := io.mAcornW.wr.resp.bits.status
  respMuxRd.bits.rdata  := io.mAcornW.rd.resp.bits.rdata
  respMuxRd.bits.status := io.mAcornW.rd.resp.bits.status

  io.sAcornS.resp << respMux
}
