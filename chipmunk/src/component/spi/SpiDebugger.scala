package chipmunk
package component.spi

import component.acorn._
import regbank._

import chisel3._
import chisel3.util._

/** Chip debugger, which allows users to access the on-chip bus through SPI.
  *
  * The bus access interface is a parameter-configurable [[AcornDpIO]] master interface. It can be connected to a AMBA
  * AXI interconnect via [[AcornDpToAxiLiteBridge]].
  *
  * @param spiClockPriority
  *   Clock priority of SPI, also known as CPOL. If true, idle state of SCK is high, otherwise low.
  * @param spiClockPhase
  *   Clock phase of SPI, also known as CPHA. If true, transmission occurs on SCK transition from active to idle,
  *   otherwise from idle to active.
  * @param busAddrWidth
  *   The bit width of the bus address.
  */
class SpiDebugger(spiClockPriority: Boolean = false, spiClockPhase: Boolean = false, busAddrWidth: Int = 32)
    extends Module {
  require(busAddrWidth <= 64, s"busAddrWidth must be less than or equal to 64 but got $busAddrWidth")

  val io = IO(new Bundle {
    val sSpi = Slave(new SpiIO)
    val mDbg = Master(new AcornDpIO(dataWidth = 32, addrWidth = busAddrWidth))
  })

  // ---------------------------------------------------------------------------
  // SPI input signal synchronization

  val spiSsnSyncRegs = ShiftRegisters(io.sSpi.ssn, 3, true.B, true.B)
  val spiSckSyncRegs = ShiftRegisters(io.sSpi.sck, 3, spiClockPriority.B, true.B)

  val spiMosiSync = ShiftRegister(io.sSpi.mosi, 3, false.B, true.B)

  val spiSckPosEdge = !spiSckSyncRegs(2) && spiSckSyncRegs(1)
  val spiSckNegEdge = !spiSckSyncRegs(1) && spiSckSyncRegs(2)
  val spiSsnPosEdge = !spiSsnSyncRegs(2) && spiSsnSyncRegs(1)
  val spiSsnNegEdge = !spiSsnSyncRegs(1) && spiSsnSyncRegs(2)

  // If CPOL = CPHA = 0/1, use SCK rising edge as sample edge, otherwise use falling edge
  val spiRxSampleEdge = if (spiClockPriority == spiClockPhase) spiSckPosEdge else spiSckNegEdge
  val spiTxSampleEdge = if (spiClockPriority != spiClockPhase) spiSckPosEdge else spiSckNegEdge

  // Delay the sample edge to trigger a internal register update
  val spiRxSampleEdgeDelay = spiTxSampleEdge
  // val spiRxSampleEdgeDelay = ShiftRegister(spiRxSampleEdge, 1, false.B, true.B)

  // ---------------------------------------------------------------------------
  // SPI controlling FSM

  object SpiState extends ChiselEnum {
    val IDLE, CMD, REG_WR_DATA, REG_RD_DATA, BUS_ADDR, BUS_WR_DATA, BUS_RD_DUMMY, BUS_RD_DATA = Value
  }

  object SpiCmd extends ChiselEnum {
    val NOP, REG_WR, REG_RD, BUS_WR, BUS_RD = Value
  }

  val spiStateCurr = RegInit(SpiState.IDLE)
  val spiStateNext = WireDefault(spiStateCurr)
  spiStateCurr := spiStateNext

  val spiStateCnt      = RegInit(0.U(5.W))
  val spiStateCntSck8  = spiStateCnt === 7.U
  val spiStateCntSck32 = spiStateCnt === 31.U
  val spiStateCntFull = MuxLookup(spiStateCurr, false.B)(
    Seq(
      SpiState.CMD          -> spiStateCntSck8,
      SpiState.REG_WR_DATA  -> spiStateCntSck32,
      SpiState.REG_RD_DATA  -> spiStateCntSck32,
      SpiState.BUS_ADDR     -> spiStateCntSck32,
      SpiState.BUS_WR_DATA  -> spiStateCntSck32,
      SpiState.BUS_RD_DUMMY -> spiStateCntSck8,
      SpiState.BUS_RD_DATA  -> spiStateCntSck32
    )
  )
  val spiStateCntDone = spiStateCntFull && spiRxSampleEdgeDelay
  when(spiStateCurr === SpiState.IDLE || spiStateCntDone) {
    spiStateCnt := 0.U
  }.elsewhen(spiRxSampleEdgeDelay) {
    spiStateCnt := spiStateCnt + 1.U
  }

  // Declare here to avoid "not fully initialized" error
  val spiCmdNext = WireDefault(SpiCmd.NOP)

  when(spiSsnPosEdge) {
    spiStateNext := SpiState.IDLE
  }.otherwise {
    switch(spiStateCurr) {
      is(SpiState.IDLE) {
        when(spiSsnNegEdge) {
          spiStateNext := SpiState.CMD
        }
      }
      is(SpiState.CMD) {
        when(spiStateCntDone) {
          spiStateNext := MuxLookup(spiCmdNext, SpiState.CMD)(
            Seq(
              SpiCmd.NOP    -> SpiState.IDLE,
              SpiCmd.REG_WR -> SpiState.REG_WR_DATA,
              SpiCmd.REG_RD -> SpiState.REG_RD_DATA,
              SpiCmd.BUS_WR -> SpiState.BUS_ADDR,
              SpiCmd.BUS_RD -> SpiState.BUS_ADDR
            )
          )
        }
      }
      is(SpiState.BUS_ADDR) {
        when(spiStateCntDone) {
          spiStateNext := MuxLookup(spiCmdNext, SpiState.IDLE)(
            Seq(SpiCmd.BUS_RD -> SpiState.BUS_RD_DUMMY, SpiCmd.BUS_WR -> SpiState.BUS_WR_DATA)
          )
        }
      }
      is(SpiState.REG_WR_DATA, SpiState.REG_RD_DATA, SpiState.BUS_WR_DATA, SpiState.BUS_RD_DATA) {
        when(spiStateCntDone) {
          spiStateNext := SpiState.IDLE
        }
      }
      is(SpiState.BUS_RD_DUMMY) {
        when(spiStateCntDone) {
          spiStateNext := SpiState.BUS_RD_DATA
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // SPI RX data

  val spiRxEnable = spiStateCurr.isOneOf(SpiState.CMD, SpiState.REG_WR_DATA, SpiState.BUS_WR_DATA, SpiState.BUS_ADDR)

  val spiRxData = RegInit(0.U(32.W))
  when(!spiRxEnable) {
    spiRxData := 0.U
  }.elsewhen(spiRxSampleEdge) {
    spiRxData := spiRxData(30, 0) ## spiMosiSync
  }

  // ---------------------------------------------------------------------------
  // SPI cmd decoding

  val spiCmdRaw = spiRxData.lsBits(8)
  when(spiCmdRaw === 0x80.U) {
    spiCmdNext := SpiCmd.BUS_WR
  }.elsewhen(spiCmdRaw === 0xc0.U) {
    spiCmdNext := SpiCmd.BUS_RD
  }.elsewhen(!spiCmdRaw(7)) {
    spiCmdNext := Mux(spiCmdRaw(6), SpiCmd.REG_RD, SpiCmd.REG_WR)
  }

  val spiCmdCurr = RegEnable(spiCmdNext, SpiCmd.NOP, spiStateCntDone && spiStateCurr === SpiState.CMD)

  // ---------------------------------------------------------------------------
  // Register file

  object RegFileAddrMap extends ChiselEnum {
    val BUS_ADDR_L, BUS_ADDR_H, BUS_WR_RESP, BUS_RD_RESP, BUS_WR_DATA, BUS_RD_DATA, BUS_WR_MASK = Value

    val TEST = Value(0x3f.U(6.W))
  }

  val regFileWrEnable = spiStateCntDone && spiStateCurr === SpiState.REG_WR_DATA
  val regFileWrAddr   = RegEnable(spiCmdRaw.lsBits(6), 0.U, spiStateCntDone && spiStateCurr === SpiState.CMD)
  val regFileRdAddr   = spiCmdRaw.lsBits(6)

  val regFileConfigs: Seq[RegElementConfig] = {
    import RegFieldAccessType._
    import RegFileAddrMap._

    val regFileConfigAddrH: Seq[RegElementConfig] = if (busAddrWidth > 32) {
      Seq(RegElementConfig("BUS_ADDR_H", addr = BUS_ADDR_H.litValue, bitCount = 32))
    } else Seq()

    regFileConfigAddrH ++ Seq(
      RegElementConfig("BUS_ADDR_L", addr = BUS_ADDR_L.litValue, bitCount = busAddrWidth min 32, backdoorUpdate = true),
      RegElementConfig(
        "BUS_WR_RESP",
        addr = BUS_WR_RESP.litValue,
        bitCount = 1,
        accessType = ReadOnly,
        backdoorUpdate = true
      ),
      RegElementConfig(
        "BUS_RD_RESP",
        addr = BUS_RD_RESP.litValue,
        bitCount = 1,
        accessType = ReadOnly,
        backdoorUpdate = true
      ),
      RegElementConfig("BUS_WR_DATA", addr = BUS_WR_DATA.litValue, bitCount = 32, backdoorUpdate = true),
      RegElementConfig("BUS_RD_DATA", addr = BUS_RD_DATA.litValue, bitCount = 32, backdoorUpdate = true),
      RegElementConfig(
        "BUS_WR_MASK",
        addr = BUS_WR_MASK.litValue,
        bitCount = io.mDbg.wr.cmd.bits.maskWidth,
        initValue = Fill(io.mDbg.wr.cmd.bits.maskWidth, true.B)
      ),
      RegElementConfig("TEST", addr = TEST.litValue, bitCount = 32, initValue = 0x3f1b_00e5.U(32.W))
    )
  }

  val uSpiRegFile = Module(new RegBank(addrWidth = 6, dataWidth = 32, regs = regFileConfigs))

  // One-cycle command valid is enough, because response channel is always ready.
  uSpiRegFile.io.access.wr.cmd.valid      := regFileWrEnable
  uSpiRegFile.io.access.wr.cmd.bits.addr  := regFileWrAddr
  uSpiRegFile.io.access.wr.cmd.bits.wdata := spiRxData
  uSpiRegFile.io.access.wr.resp.ready     := true.B

  uSpiRegFile.io.access.rd.cmd.valid     := true.B
  uSpiRegFile.io.access.rd.cmd.bits.addr := regFileRdAddr
  uSpiRegFile.io.access.rd.resp.ready    := true.B

  val regRdData = uSpiRegFile.io.access.rd.resp.bits.rdata

  uSpiRegFile.io.fields("BUS_RD_DATA").backdoorUpdate.get.valid := io.mDbg.rd.resp.fire
  uSpiRegFile.io.fields("BUS_RD_DATA").backdoorUpdate.get.bits  := io.mDbg.rd.resp.bits.rdata

  uSpiRegFile.io.fields("BUS_WR_RESP").backdoorUpdate.get.valid := io.mDbg.wr.resp.fire
  uSpiRegFile.io.fields("BUS_WR_RESP").backdoorUpdate.get.bits  := io.mDbg.wr.resp.bits.status.asUInt

  uSpiRegFile.io.fields("BUS_RD_RESP").backdoorUpdate.get.valid := io.mDbg.rd.resp.fire
  uSpiRegFile.io.fields("BUS_RD_RESP").backdoorUpdate.get.bits  := io.mDbg.rd.resp.bits.status.asUInt

  val busAddrBufUpdate = spiStateCntDone && spiStateCurr === SpiState.BUS_ADDR
  uSpiRegFile.io.fields("BUS_ADDR_L").backdoorUpdate.get.valid := busAddrBufUpdate
  uSpiRegFile.io.fields("BUS_ADDR_L").backdoorUpdate.get.bits  := spiRxData.lsBits(busAddrWidth min 32)

  val busWrDataBufUpdate = spiStateCntDone && spiStateCurr === SpiState.BUS_WR_DATA
  uSpiRegFile.io.fields("BUS_WR_DATA").backdoorUpdate.get.valid := busWrDataBufUpdate
  uSpiRegFile.io.fields("BUS_WR_DATA").backdoorUpdate.get.bits  := spiRxData

  // ---------------------------------------------------------------------------
  // Bus access

  // It does NOT check whether the previous request is finished before issuing a new request.
  // If a new request is issued before the previous one is finished, it may cause some unexpected behavior. To avoid
  // this, users should ensure that the SPI clock frequency is much slower than the on-chip bus access.

  val busWrReqFast = spiStateCurr === SpiState.BUS_WR_DATA && spiStateNext === SpiState.IDLE
  val busRdReqFast = spiStateCurr === SpiState.BUS_ADDR && spiStateNext === SpiState.BUS_RD_DUMMY
  val busWrReqNorm = uSpiRegFile.io.fields("BUS_WR_RESP").isBeingWritten
  val busRdReqNorm = uSpiRegFile.io.fields("BUS_RD_RESP").isBeingWritten

  val busWrReqValid: Bool = RegInit(false.B)
  when(busWrReqFast || busWrReqNorm) {
    busWrReqValid := true.B
  }.elsewhen(io.mDbg.wr.cmd.fire) {
    busWrReqValid := false.B
  }

  val buRdReqValid: Bool = RegInit(false.B)
  when(busRdReqFast || busRdReqNorm) {
    buRdReqValid := true.B
  }.elsewhen(io.mDbg.rd.cmd.fire) {
    buRdReqValid := false.B
  }

  io.mDbg.wr.cmd.valid := busWrReqValid
  io.mDbg.rd.cmd.valid := buRdReqValid

  io.mDbg.wr.cmd.bits.wdata := uSpiRegFile.io.fields("BUS_WR_DATA").value
  io.mDbg.wr.cmd.bits.wmask := uSpiRegFile.io.fields("BUS_WR_MASK").value

  if (busAddrWidth > 32) {
    io.mDbg.wr.cmd.bits.addr := uSpiRegFile.io.fields("BUS_ADDR_H").value ## uSpiRegFile.io.fields("BUS_ADDR_L").value
    io.mDbg.rd.cmd.bits.addr := uSpiRegFile.io.fields("BUS_ADDR_H").value ## uSpiRegFile.io.fields("BUS_ADDR_L").value
  } else {
    io.mDbg.wr.cmd.bits.addr := uSpiRegFile.io.fields("BUS_ADDR_L").value
    io.mDbg.rd.cmd.bits.addr := uSpiRegFile.io.fields("BUS_ADDR_L").value
  }

  io.mDbg.wr.resp.ready := true.B
  io.mDbg.rd.resp.ready := true.B

  // ---------------------------------------------------------------------------
  // SPI TX data

  val spiTxEnable = spiStateCurr.isOneOf(SpiState.BUS_RD_DATA, SpiState.REG_RD_DATA)
  val spiTxData   = RegInit(0.U(32.W))
  when(spiStateCurr === SpiState.CMD && spiStateNext === SpiState.REG_RD_DATA) {
    spiTxData := regRdData
  }.elsewhen(spiStateCurr === SpiState.BUS_RD_DUMMY && spiStateNext === SpiState.BUS_RD_DATA) {
    spiTxData := uSpiRegFile.io.fields("BUS_RD_DATA").value
  }.elsewhen(spiTxEnable && spiTxSampleEdge) {
    spiTxData := spiTxData << 1
  }

  io.sSpi.miso := spiTxData(31)
}
