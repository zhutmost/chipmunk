package chipmunk
package component.acorn

import amba._
import chisel3._
import chisel3.util._

class Axi4ToAcornDpBridge(dataWidth: Int = 32, addrWidth: Int = 32, idWidth: Int = 0) extends Module {
  val io = IO(new Bundle {
    val sAxi4   = Slave(new Axi4IO(dataWidth = dataWidth, addrWidth = addrWidth, idWidth = idWidth))
    val mAcornW = Master(new AcornDpIO(dataWidth = dataWidth, addrWidth = addrWidth))
  })

  def burstAddrNext(addr: UInt, burst: AxiBurstType.Type, len: UInt): UInt = {
    val addrNext = Wire(UInt(addrWidth.W))
    addrNext := MuxLookup(burst, addr) {
      Seq(
        AxiBurstType.BURST_FIXED -> addr,
        AxiBurstType.BURST_INCR -> {
          addr + io.sAxi4.dataWidthByteNum.U
        },
        AxiBurstType.BURST_WRAP -> {
          val wrapSize = Wire(UInt(32.W))
          wrapSize := len << log2Ceil(io.sAxi4.dataWidthByteNum)
          val wrapEnable = (addr & wrapSize) === wrapSize
          Mux(wrapEnable, addr - wrapSize, addr + io.sAxi4.dataWidthByteNum.U)
        }
      )
    }
    addrNext
  }

  // ---------------------------------------------------------------------------
  // Read

  object ReadState extends ChiselEnum {
    val IDLE, BUSY = Value
  }

  val readCmdState  = RegInit(ReadState.IDLE)
  val readRespState = RegInit(ReadState.IDLE)
  val readCmdCnt    = RegInit(0.U(8.W))
  val readRespCnt   = RegInit(0.U(8.W))

  val arAddr  = RegInit(0.U(io.sAxi4.addrWidth.W))
  val arBurst = RegInit(AxiBurstType.BURST_INCR)
  val arLen   = RegInit(0.U(8.W))
  val arId    = if (io.sAxi4.hasId) Some(RegInit(0.U(io.sAxi4.idWidth.W))) else None

  io.mAcornW.rd.cmd.valid     := readCmdState === ReadState.BUSY
  io.mAcornW.rd.cmd.bits.addr := arAddr

  io.sAxi4.ar.ready := readCmdState === ReadState.IDLE && readRespState === ReadState.IDLE

  io.sAxi4.r handshakeFrom io.mAcornW.rd.resp
  io.sAxi4.r.bits.data := io.mAcornW.rd.resp.bits.rdata
  io.sAxi4.r.bits.resp := Mux(io.mAcornW.rd.resp.bits.status, AxiResp.RESP_SLVERR, AxiResp.RESP_OKAY)
  io.sAxi4.r.bits.last := readRespCnt === arLen

  switch(readCmdState) {
    is(ReadState.IDLE) {
      readCmdCnt := 0.U
      when(io.sAxi4.ar.fire) {
        readCmdState := ReadState.BUSY
        arAddr       := io.sAxi4.ar.bits.addr
        arBurst      := io.sAxi4.ar.bits.burst
        arLen        := io.sAxi4.ar.bits.len
        if (io.sAxi4.hasId) {
          arId.get := io.sAxi4.ar.bits.id.get
        }
      }
    }
    is(ReadState.BUSY) {
      when(io.mAcornW.rd.cmd.fire) {
        readCmdCnt := readCmdCnt + 1.U
        when(readCmdCnt === arLen) {
          readCmdState := ReadState.IDLE
        }
        arAddr := burstAddrNext(arAddr, arBurst, arLen)
      }
    }
  }

  switch(readRespState) {
    is(ReadState.IDLE) {
      readRespCnt := 0.U
      when(io.sAxi4.ar.fire) {
        readRespState := ReadState.BUSY
      }
    }
    is(ReadState.BUSY) {
      when(io.mAcornW.rd.resp.fire) {
        readRespCnt := readRespCnt + 1.U
        when(readRespCnt === arLen) {
          readRespState := ReadState.IDLE
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Write

  object WriteState extends ChiselEnum {
    val IDLE, BUSY, WAIT_RESP, ACK_RESP = Value
  }

  val writeCmdState = RegInit(WriteState.IDLE)

//  val writeCmdBusy  = RegInit(false.B)
  val writeRespBusy = RegInit(false.B)
  val writeCmdCnt   = RegInit(0.U(8.W))
  val writeRespCnt  = RegInit(0.U(8.W))

  val awReady = RegInit(false.B)
  val awAddr  = RegInit(0.U(io.sAxi4.addrWidth.W))
  val awBurst = RegInit(AxiBurstType.BURST_INCR)
  val awLen   = RegInit(0.U(8.W))
  val awId    = if (idWidth > 0) Some(RegInit(0.U(io.sAxi4.idWidth.W))) else None

  //  val wLast = RegInit(false.B)

  val bValid = RegInit(false.B)
  val bId    = if (idWidth > 0) Some(RegInit(0.U(io.sAxi4.idWidth.W))) else None
  val bResp  = RegInit(AxiResp.RESP_OKAY)

  io.sAxi4.aw.ready := writeCmdState === WriteState.IDLE
  io.sAxi4.w.ready  := writeCmdState === WriteState.BUSY && io.mAcornW.wr.cmd.ready
  io.sAxi4.b.valid  := bValid
  if (io.sAxi4.hasId) { io.sAxi4.b.bits.id.get := bId.get }

  io.mAcornW.wr.cmd.valid      := writeCmdState === WriteState.BUSY && io.sAxi4.w.valid
  io.mAcornW.wr.cmd.bits.addr  := awAddr
  io.mAcornW.wr.cmd.bits.wdata := io.sAxi4.w.bits.data
  io.mAcornW.wr.cmd.bits.wmask := io.sAxi4.w.bits.strb

  switch(writeCmdState) {
    is(WriteState.IDLE) {
      bValid := false.B
      when(io.sAxi4.aw.fire) {
        writeCmdState := WriteState.BUSY
        awAddr        := io.sAxi4.aw.bits.addr
        awLen         := io.sAxi4.aw.bits.len
        writeCmdCnt   := 0.U
        if (io.sAxi4.hasId) {
          bId.get := io.sAxi4.aw.bits.id.get
        }
      }
    }
    is(WriteState.BUSY) {
      when(io.sAxi4.w.fire) {
        writeCmdCnt := writeCmdCnt + 1.U
        when(writeCmdCnt === awLen) {
          writeCmdState := WriteState.WAIT_RESP
        }
        awAddr := burstAddrNext(awAddr, awBurst, awLen)
      }
    }
    is(WriteState.WAIT_RESP) {
      when(writeRespBusy && io.mAcornW.wr.resp.fire && writeRespCnt === awLen) {
        when(io.sAxi4.b.isStarving) {
          writeCmdState := WriteState.IDLE
          bValid        := true.B
        }.otherwise {
          writeCmdState := WriteState.ACK_RESP
        }
      }
    }
    is(WriteState.ACK_RESP) {
      when(io.sAxi4.b.fire) {
        writeCmdState := WriteState.IDLE
        bValid        := false.B
      }
    }
  }

  when(!writeRespBusy) {
    writeRespCnt := 0.U
    when(io.sAxi4.aw.fire) {
      writeRespBusy := true.B
    }
  }.otherwise {
    when(io.mAcornW.wr.resp.fire) {
      writeRespCnt := writeRespCnt + 1.U
      when(writeRespCnt === awLen) {
        writeRespBusy := false.B
      }
    }
  }
}
