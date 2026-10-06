package chipmunk
package spi

import chisel3.*
import chisel3.util.{is, switch, Cat, ShiftRegister, ShiftRegisters}

import chipmunk.acorn.{AcornIO, AcornParams}
import chipmunk.regbank.{RegBank, RegBankConfig, RegElementConfig, RegFieldAccessType}

object SpiDebugger {

  /** Six-bit SPI register indices; the internal RegBank byte offset is index * 4. */
  object Registers {
    val BUS_ADDR_L  = 0x00
    val BUS_ADDR_H  = 0x01
    val BUS_WR_RESP = 0x02
    val BUS_RD_RESP = 0x03
    val BUS_WR_DATA = 0x04
    val BUS_RD_DATA = 0x05
    val BUS_WR_MASK = 0x06
    val STATUS      = 0x07
    val TEST        = 0x3f
  }

  /** STATUS bit masks. An admitted request clears DONE, REJECTED and EARLY_READ. */
  object Status {
    val BUSY       = 1 << 0
    val RD_DONE    = 1 << 1
    val WR_DONE    = 1 << 2
    val REJECTED   = 1 << 3
    val EARLY_READ = 1 << 4
  }

  val earlyReadValue: BigInt = BigInt("DEADBEEF", 16)
}

/** SPI slave debugger with a 32-bit, byte-addressed Acorn master.
  *
  * Transfers are MSB-first, with one command per active-low SSN frame. The system clock frequency must be at least
  * eight times the SCK frequency, assuming approximately 50% SCK duty cycle and sufficient IO setup/hold margin. SSN
  * must be observed active before the first sample edge and remain active through the last sample edge. These are host
  * timing requirements, not checks enforced by this module.
  *
  * Commands: REG_WR = 00iiiiii + data32; REG_RD = 01iiiiii + data32; BUS_WR = 0x80 + address32 + data32; BUS_RD = 0xc0
  * + address32 + dummy8 + data32. Other 1xxxxxxx commands are NOPs. BUS_ADDR_H supplies upper address bits when
  * busAddrWidth > 32. Writing BUS_WR_RESP or BUS_RD_RESP triggers a staged bus write or read.
  *
  * Only one bus transaction may be pending. Requests snapshot address/data/strobe and hold them through command
  * backpressure. A trigger while BUSY sets REJECTED without replacing that transaction. Unaligned or overflowing
  * addresses complete locally with an error. RESP bit zero reports the last completed operation's error.
  *
  * Poll STATUS.RD_DONE before reading BUS_RD_DATA for arbitrary bus latency. A fast BUS_RD snapshots its result at the
  * end of the dummy phase; if its own result is unavailable, it returns 0xDEAD_BEEF and sets EARLY_READ. A late
  * response still updates BUS_RD_DATA and RD_DONE. Check STATUS: 0xDEAD_BEEF can also be valid bus data.
  *
  * An incomplete REG_WR does not write its target register; an incomplete BUS_WR does not issue a bus write. Fast
  * commands update address staging when their address word completes. Deselecting SPI does not cancel a submitted bus
  * request. Reset cancels transactions and must reset the connected Acorn endpoints consistently.
  *
  * @param hasMisoValid
  *   expose a MISO IO-cell output enable, qualified directly by SSN for immediate release on deselection
  * @param cpol
  *   idle SCK level
  * @param cpha
  *   sample on the trailing edge when true, otherwise on the leading edge
  * @param busAddrWidth
  *   Acorn address width, from 2 to 64 bits
  */
class SpiDebugger(hasMisoValid: Boolean = false, cpol: Boolean = false, cpha: Boolean = false, busAddrWidth: Int = 32)
    extends Module {
  require(busAddrWidth >= 2 && busAddrWidth <= 64, "SpiDebugger busAddrWidth must be between 2 and 64.")

  import SpiDebugger.Registers.*

  private val busParams = AcornParams(32, busAddrWidth)
  val io                = IO(new Bundle {
    val sSpi = Slave(new SpiIO(hasMisoValid))
    val mDbg = Master(new AcornIO(busParams))
  })

  // Two synchronizer stages followed by edge history; MOSI uses the corresponding delayed sample.
  private val ssn      = ShiftRegisters(io.sSpi.ssn, 3, true.B, true.B)
  private val sck      = ShiftRegisters(io.sSpi.sck, 3, cpol.B, true.B)
  private val mosi     = ShiftRegister(io.sSpi.mosi, 3, false.B, true.B)
  private val selected = !ssn(1)
  private val select   = ssn(2) && !ssn(1)
  private val rising   = !sck(2) && sck(1)
  private val falling  = sck(2) && !sck(1)
  private val sample   = selected && (if (cpol == cpha) rising else falling)
  private val launch   = selected && (if (cpol == cpha) falling else rising)

  private object State extends ChiselEnum {
    val Idle, Command, RegWrite, RegRead, BusAddress, BusWrite, ReadDummy, BusRead, Done = Value
  }
  private val state            = RegInit(State.Idle)
  private val count            = RegInit(0.U(5.W))
  private val rx               = RegInit(0.U(32.W))
  private val rxNext           = Cat(rx(30, 0), mosi)
  private val bytePhase        = state.isOneOf(State.Command, State.ReadDummy)
  private val last             = count === Mux(bytePhase, 7.U, 31.U)
  private val receiving        = state.isOneOf(State.Command, State.RegWrite, State.BusAddress, State.BusWrite)
  private val transmitting     = state.isOneOf(State.RegRead, State.BusRead)
  private val active           = !state.isOneOf(State.Idle, State.Done)
  private val wordDone         = sample && active && last
  private val addressDone      = wordDone && state === State.BusAddress
  private val writeDone        = wordDone && state === State.BusWrite
  private val frameRead        = RegInit(false.B)
  private val frameAddress     = RegInit(0.U(64.W))
  private val frameOverflow    = RegInit(false.B)
  private val fastReadAdmitted = RegInit(false.B)

  private val tx        = RegInit(0.U(32.W))
  private val txActive  = RegInit(false.B)
  private val txSampled = RegInit(false.B)
  io.sSpi.miso := tx(31)
  // Only pad output enable uses raw SSN; protocol state uses synchronized inputs.
  io.sSpi.misoValid.foreach(_ := !io.sSpi.ssn)

  private val registerConfigs = {
    import RegFieldAccessType.ReadOnly
    val high = if (busAddrWidth > 32) {
      Seq(RegElementConfig("BUS_ADDR_H", BUS_ADDR_H * 4, busAddrWidth - 32))
    } else Seq.empty
    high ++ Seq(
      RegElementConfig("BUS_ADDR_L", BUS_ADDR_L * 4, busAddrWidth min 32, backdoorUpdate = true),
      RegElementConfig("BUS_WR_RESP", BUS_WR_RESP * 4, 1, accessType = ReadOnly, backdoorUpdate = true),
      RegElementConfig("BUS_RD_RESP", BUS_RD_RESP * 4, 1, accessType = ReadOnly, backdoorUpdate = true),
      RegElementConfig("BUS_WR_DATA", BUS_WR_DATA * 4, 32, backdoorUpdate = true),
      RegElementConfig("BUS_RD_DATA", BUS_RD_DATA * 4, 32, backdoorUpdate = true),
      RegElementConfig(
        "BUS_WR_MASK",
        BUS_WR_MASK * 4,
        busParams.strobeWidth,
        initValue = (BigInt(1) << busParams.strobeWidth) - 1,
      ),
      RegElementConfig("STATUS", STATUS * 4, 5, accessType = ReadOnly, backdoorUpdate = true),
      RegElementConfig("TEST", TEST * 4, 32, initValue = BigInt("3f1b00e5", 16)),
    )
  }
  private val bank                      = Module(new RegBank(RegBankConfig(AcornParams(32, 8), registerConfigs)))
  private def value(name: String): UInt = bank.io.fields(name).value
  private def update(name: String, enable: Bool, data: UInt): Unit = {
    val port = bank.io.fields(name).backdoorUpdate.get
    port.valid := enable
    port.bits  := data
  }

  private val regIndex = RegInit(0.U(6.W))
  private val regWrite = RegInit(false.B)
  private val regRead  = RegInit(false.B)
  private val regData  = RegInit(0.U(32.W))
  bank.io.access.wr.cmd.valid       := regWrite
  bank.io.access.wr.cmd.bits.addr   := Cat(regIndex, 0.U(2.W))
  bank.io.access.wr.cmd.bits.data   := regData
  bank.io.access.wr.cmd.bits.strobe := ((BigInt(1) << busParams.strobeWidth) - 1).U
  bank.io.access.wr.rsp.ready       := true.B
  bank.io.access.rd.cmd.valid       := regRead
  bank.io.access.rd.cmd.bits.addr   := Cat(regIndex, 0.U(2.W))
  bank.io.access.rd.rsp.ready       := true.B
  when(bank.io.access.wr.cmd.fire) { regWrite := false.B }
  when(bank.io.access.rd.cmd.fire) { regRead := false.B }

  // Retain overflow information that narrow address fields otherwise discard on software writes.
  private val lowOverflow                   = RegInit(false.B)
  private val highOverflow                  = RegInit(false.B)
  private def overflowLow(data: UInt): Bool =
    if (busAddrWidth < 32) data(31, busAddrWidth).orR else false.B
  private def overflowHigh(data: UInt): Bool =
    if (busAddrWidth > 32 && busAddrWidth < 64) data(31, busAddrWidth - 32).orR else false.B
  when(bank.io.fields("BUS_ADDR_L").isBeingWritten) { lowOverflow := overflowLow(regData) }
  if (busAddrWidth > 32) {
    when(bank.io.fields("BUS_ADDR_H").isBeingWritten) { highOverflow := overflowHigh(regData) }
  }
  private val upperAddress    = if (busAddrWidth > 32) value("BUS_ADDR_H").pad(32) else 0.U(32.W)
  private val stagedAddress   = Cat(upperAddress, value("BUS_ADDR_L").pad(32))
  private val receivedAddress = Cat(upperAddress, rxNext)
  when(addressDone) {
    frameAddress  := receivedAddress
    frameOverflow := overflowLow(rxNext) || highOverflow
    lowOverflow   := overflowLow(rxNext)
  }
  update("BUS_ADDR_L", addressDone, rxNext(busAddrWidth.min(32) - 1, 0))
  update("BUS_WR_DATA", writeDone, rxNext)

  private val busy       = RegInit(false.B)
  private val rdDone     = RegInit(false.B)
  private val wrDone     = RegInit(false.B)
  private val rejected   = RegInit(false.B)
  private val earlyRead  = RegInit(false.B)
  private val readResult = RegInit(0.U(32.W))
  update("STATUS", true.B, Cat(earlyRead, rejected, wrDone, rdDone, busy))

  private val fastRead        = addressDone && frameRead
  private val fastWrite       = writeDone
  private val stagedRead      = bank.io.fields("BUS_RD_RESP").isBeingWritten
  private val stagedWrite     = bank.io.fields("BUS_WR_RESP").isBeingWritten
  private val trigger         = fastRead || fastWrite || stagedRead || stagedWrite
  private val requestRead     = fastRead || stagedRead
  private val requestAddress  = Mux(fastRead, receivedAddress, Mux(fastWrite, frameAddress, stagedAddress))
  private val requestOverflow =
    Mux(fastRead, overflowLow(rxNext) || highOverflow, Mux(fastWrite, frameOverflow, lowOverflow || highOverflow))
  private val invalid      = requestOverflow || requestAddress(1, 0).orR
  private val admit        = trigger && !busy
  private val invalidRead  = admit && invalid && requestRead
  private val invalidWrite = admit && invalid && !requestRead

  private val commandPending   = RegInit(false.B)
  private val awaitingResponse = RegInit(false.B)
  private val pendingRead      = RegInit(false.B)
  private val pendingAddress   = RegInit(0.U(busAddrWidth.W))
  private val pendingData      = RegInit(0.U(32.W))
  private val pendingStrobe    = RegInit(0.U(busParams.strobeWidth.W))
  io.mDbg.rd.cmd.valid       := commandPending && pendingRead
  io.mDbg.rd.cmd.bits.addr   := pendingAddress
  io.mDbg.wr.cmd.valid       := commandPending && !pendingRead
  io.mDbg.wr.cmd.bits.addr   := pendingAddress
  io.mDbg.wr.cmd.bits.data   := pendingData
  io.mDbg.wr.cmd.bits.strobe := pendingStrobe
  io.mDbg.rd.rsp.ready       := busy && pendingRead && (awaitingResponse || io.mDbg.rd.cmd.fire)
  io.mDbg.wr.rsp.ready       := busy && !pendingRead && (awaitingResponse || io.mDbg.wr.cmd.fire)
  private val readResponse  = io.mDbg.rd.rsp.fire
  private val writeResponse = io.mDbg.wr.rsp.fire
  private val responseData  = Mux(io.mDbg.rd.rsp.bits.error, 0.U, io.mDbg.rd.rsp.bits.data)

  when(io.mDbg.rd.cmd.fire || io.mDbg.wr.cmd.fire) {
    commandPending   := false.B
    awaitingResponse := true.B
  }
  when(readResponse || writeResponse) {
    busy             := false.B
    awaitingResponse := false.B
  }
  when(readResponse) {
    rdDone     := true.B
    readResult := responseData
  }
  when(writeResponse) { wrDone := true.B }
  when(trigger) {
    when(busy) {
      rejected := true.B
    }.otherwise {
      busy             := !invalid
      commandPending   := !invalid
      awaitingResponse := false.B
      pendingRead      := requestRead
      pendingAddress   := requestAddress(busAddrWidth - 1, 0)
      pendingData      := Mux(fastWrite, rxNext, value("BUS_WR_DATA"))
      pendingStrobe    := value("BUS_WR_MASK")
      rdDone           := invalid && requestRead
      wrDone           := invalid && !requestRead
      rejected         := false.B
      earlyRead        := false.B
      when(invalid && requestRead) { readResult := 0.U }
    }
  }
  update("BUS_RD_DATA", readResponse || invalidRead, Mux(invalidRead, 0.U, responseData))
  update("BUS_RD_RESP", readResponse || invalidRead, (invalidRead || io.mDbg.rd.rsp.bits.error).asUInt)
  update("BUS_WR_RESP", writeResponse || invalidWrite, (invalidWrite || io.mDbg.wr.rsp.bits.error).asUInt)

  // Shift only after the preceding data bit has been sampled; the first launch keeps the preloaded MSB.
  when(launch && txActive && txSampled) {
    tx        := Cat(tx(30, 0), false.B)
    txSampled := false.B
  }
  when(!selected) {
    state            := State.Idle
    count            := 0.U
    rx               := 0.U
    tx               := 0.U
    txActive         := false.B
    txSampled        := false.B
    fastReadAdmitted := false.B
  }.elsewhen(select) {
    state            := State.Command
    count            := 0.U
    rx               := 0.U
    txActive         := false.B
    txSampled        := false.B
    fastReadAdmitted := false.B
  }.elsewhen(sample && active) {
    count := Mux(last, 0.U, count + 1.U)
    when(receiving) { rx := Mux(last, 0.U, rxNext) }
    when(transmitting) { txSampled := true.B }
    when(last) {
      switch(state) {
        is(State.Command) {
          regIndex := rxNext(5, 0)
          when(rxNext(7, 0) === 0x80.U || rxNext(7, 0) === 0xc0.U) {
            frameRead := rxNext(6)
            state     := State.BusAddress
          }.elsewhen(!rxNext(7)) {
            when(rxNext(6)) {
              regRead := true.B
              state   := State.RegRead
            }.otherwise {
              state := State.RegWrite
            }
          }.otherwise { state := State.Done }
        }
        is(State.RegWrite) {
          regData  := rxNext
          regWrite := true.B
          state    := State.Done
        }
        is(State.BusAddress) {
          when(frameRead) {
            fastReadAdmitted := !busy
            state            := State.ReadDummy
          }.otherwise { state := State.BusWrite }
        }
        is(State.BusWrite) { state := State.Done }
        is(State.ReadDummy) {
          val available = fastReadAdmitted && (rdDone || readResponse)
          tx        := Mux(available, Mux(readResponse, responseData, readResult), SpiDebugger.earlyReadValue.U(32.W))
          txActive  := true.B
          txSampled := false.B
          when(!available) { earlyRead := true.B }
          state := State.BusRead
        }
        is(State.RegRead, State.BusRead) {
          txActive := false.B
          state    := State.Done
        }
      }
    }
  }
  when(bank.io.access.rd.rsp.fire && selected && state === State.RegRead) {
    tx        := bank.io.access.rd.rsp.bits.data
    txActive  := true.B
    txSampled := false.B
  }
}
