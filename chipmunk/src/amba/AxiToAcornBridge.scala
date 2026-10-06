package chipmunk
package amba

import chisel3.*
import chisel3.util.{log2Ceil, Queue}

import chipmunk.acorn.{AcornIO, AcornOutstanding, AcornParams}
import chipmunk.stream.StreamQueue

/** Split same-width AXI4 bursts into ordered Acorn word accesses.
  *
  * Supports naturally aligned, full-width INCR/FIXED/WRAP accesses, including partial and zero write strobes. Narrow,
  * unaligned, or overflowing accesses return SLVERR without issuing Acorn commands. One burst per direction is active;
  * reads and writes proceed independently. W has a two-entry queue and may arrive before AW. The read capacity reserves
  * space for both pending Acorn responses and buffered AXI R beats; the write capacity limits pending Acorn responses.
  * B is held until accepted and is generated only after every Acorn write response has been collected.
  *
  * PROT/CACHE/QoS/REGION are not interpreted. Exclusive accesses execute as ordinary accesses and never return EXOKAY.
  * Protocol violations, including invalid burst lengths, crossing 4KB, and inconsistent WLAST, are checked by
  * assertions. All endpoints share clock/reset; reset cancels outstanding transactions and must reset the endpoints
  * consistently.
  */
final class AxiToAcornBridge(val params: AxiParams, outstanding: AcornOutstanding = AcornOutstanding()) extends Module {
  require(params.addrWidth >= params.fullSize, "AXI address width must cover one full-width word.")

  private val acornParams = AcornParams(params.dataWidth, params.addrWidth)

  val io = IO(new Bundle {
    val sAxi   = Slave(new AxiIO(params))
    val mAcorn = Master(new AcornIO(acornParams))
  })

  private val readActive   = RegInit(false.B)
  private val readRejected = Reg(Bool())
  private val readAddress  = Reg(UInt(params.addrWidth.W))
  private val readBurst    = Reg(AxiBurstType())
  private val readWrapMask = Reg(UInt(params.addrWidth.W))
  private val readBeats    = Reg(UInt(9.W))
  private val readIssued   = Reg(UInt(9.W))
  private val readReturned = Reg(UInt(9.W))
  private val readId       = if (params.hasId) Some(Reg(UInt(params.idWidth.W))) else None
  private val readReserved = RegInit(0.U(log2Ceil(BigInt(outstanding.read) + 1).W))

  private val readResponses = Module(
    new Queue(new AxiReadDataChannel(params), outstanding.read, pipe = false, flow = false)
  )

  io.sAxi.ar.ready := !readActive
  private val readSupported = validateAddress(io.sAxi.ar.bits, io.sAxi.ar.fire)
  when(io.sAxi.ar.fire) {
    readActive   := true.B
    readRejected := !readSupported
    readAddress  := io.sAxi.ar.bits.addr
    readBurst    := io.sAxi.ar.bits.burst
    readWrapMask := wrapMask(io.sAxi.ar.bits.len)
    readBeats    := io.sAxi.ar.bits.len +& 1.U
    readIssued   := 0.U
    readReturned := 0.U
    readId.foreach(_ := io.sAxi.ar.bits.id.get)
  }

  io.mAcorn.rd.cmd.valid :=
    readActive && !readRejected && readIssued < readBeats && readReserved < outstanding.read.U
  io.mAcorn.rd.cmd.bits.addr := readAddress
  when(io.mAcorn.rd.cmd.fire) {
    readIssued  := readIssued + 1.U
    readAddress := nextAddress(readAddress, readBurst, readWrapMask)
  }

  readResponses.io.enq.valid :=
    readActive && Mux(readRejected, readReturned < readBeats, io.mAcorn.rd.rsp.valid)
  readResponses.io.enq.bits.data := Mux(readRejected, 0.U, io.mAcorn.rd.rsp.bits.data)
  readResponses.io.enq.bits.resp :=
    Mux(readRejected || io.mAcorn.rd.rsp.bits.error, AxiResp.RESP_SLVERR, AxiResp.RESP_OKAY)
  readResponses.io.enq.bits.last := readReturned + 1.U === readBeats
  readResponses.io.enq.bits.id.foreach(_ := readId.get)
  io.mAcorn.rd.rsp.ready := readActive && !readRejected && readResponses.io.enq.ready

  when(readResponses.io.enq.fire) {
    readReturned := readReturned + 1.U
  }
  when(io.mAcorn.rd.rsp.fire) {
    assert(readReturned < readIssued || io.mAcorn.rd.cmd.fire, "Acorn returned an unsolicited read response.")
  }

  io.sAxi.r.valid            := readResponses.io.deq.valid
  io.sAxi.r.bits             := readResponses.io.deq.bits
  readResponses.io.deq.ready := io.sAxi.r.ready
  private val readReleased = io.sAxi.r.fire && !readRejected
  when(io.mAcorn.rd.cmd.fire =/= readReleased) {
    readReserved := Mux(io.mAcorn.rd.cmd.fire, readReserved + 1.U, readReserved - 1.U)
  }
  when(io.sAxi.r.fire && io.sAxi.r.bits.last) {
    readActive := false.B
  }

  private val writeActive   = RegInit(false.B)
  private val writeRejected = Reg(Bool())
  private val writeAddress  = Reg(UInt(params.addrWidth.W))
  private val writeBurst    = Reg(AxiBurstType())
  private val writeWrapMask = Reg(UInt(params.addrWidth.W))
  private val writeBeats    = Reg(UInt(9.W))
  private val writeTaken    = Reg(UInt(9.W))
  private val writeReturned = Reg(UInt(9.W))
  private val writeId       = if (params.hasId) Some(Reg(UInt(params.idWidth.W))) else None
  private val writeError    = Reg(Bool())
  private val writePending  = RegInit(0.U(log2Ceil(BigInt(outstanding.write) + 1).W))
  private val writeResponse = RegInit(false.B)
  private val writeData     = StreamQueue(io.sAxi.w, entries = 2, pipe = false, flow = false)

  io.sAxi.aw.ready := !writeActive
  private val writeSupported = validateAddress(io.sAxi.aw.bits, io.sAxi.aw.fire)
  when(io.sAxi.aw.fire) {
    writeActive   := true.B
    writeRejected := !writeSupported
    writeAddress  := io.sAxi.aw.bits.addr
    writeBurst    := io.sAxi.aw.bits.burst
    writeWrapMask := wrapMask(io.sAxi.aw.bits.len)
    writeBeats    := io.sAxi.aw.bits.len +& 1.U
    writeTaken    := 0.U
    writeReturned := 0.U
    writeError    := !writeSupported
    writeId.foreach(_ := io.sAxi.aw.bits.id.get)
  }

  private val writeCanIssue = writeActive && writeTaken < writeBeats
  private val writeSpace    = writePending < outstanding.write.U
  io.mAcorn.wr.cmd.valid       := writeCanIssue && !writeRejected && writeData.valid && writeSpace
  io.mAcorn.wr.cmd.bits.addr   := writeAddress
  io.mAcorn.wr.cmd.bits.data   := writeData.bits.data
  io.mAcorn.wr.cmd.bits.strobe := writeData.bits.strb
  writeData.ready              := writeCanIssue && Mux(writeRejected, true.B, writeSpace && io.mAcorn.wr.cmd.ready)

  when(writeData.fire) {
    val last = writeTaken + 1.U === writeBeats
    assert(writeData.bits.last === last, "AXI WLAST must agree with AWLEN.")
    writeTaken := writeTaken + 1.U
    when(writeRejected && last) {
      writeResponse := true.B
    }
  }
  when(io.mAcorn.wr.cmd.fire) {
    writeAddress := nextAddress(writeAddress, writeBurst, writeWrapMask)
  }

  // Consume every Acorn response independently of AXI BREADY, including the last beat's error.
  io.mAcorn.wr.rsp.ready := writeActive && !writeRejected && !writeResponse
  when(io.mAcorn.wr.rsp.fire) {
    assert(writeReturned < writeTaken || io.mAcorn.wr.cmd.fire, "Acorn returned an unsolicited write response.")
    writeReturned := writeReturned + 1.U
    writeError    := writeError || io.mAcorn.wr.rsp.bits.error
    when(writeReturned + 1.U === writeBeats) {
      writeResponse := true.B
    }
  }
  when(io.mAcorn.wr.cmd.fire =/= io.mAcorn.wr.rsp.fire) {
    writePending := Mux(io.mAcorn.wr.cmd.fire, writePending + 1.U, writePending - 1.U)
  }

  io.sAxi.b.valid     := writeResponse
  io.sAxi.b.bits.resp := Mux(writeError, AxiResp.RESP_SLVERR, AxiResp.RESP_OKAY)
  io.sAxi.b.bits.id.foreach(_ := writeId.get)
  when(io.sAxi.b.fire) {
    writeResponse := false.B
    writeActive   := false.B
  }

  private def wrapMask(length: UInt): UInt = ((length +& 1.U) << params.fullSize) - 1.U

  private def nextAddress(address: UInt, burst: AxiBurstType.Type, mask: UInt): UInt = {
    val incremented = Wire(UInt(params.addrWidth.W))
    incremented := address + params.strobeWidth.U
    Mux(
      burst === AxiBurstType.BURST_FIXED,
      address,
      Mux(burst === AxiBurstType.BURST_WRAP, (address & ~mask) | (incremented & mask), incremented),
    )
  }

  private def validateAddress(address: AxiWriteAddrChannel, accepted: Bool): Bool = {
    val fixed       = address.burst === AxiBurstType.BURST_FIXED
    val incr        = address.burst === AxiBurstType.BURST_INCR
    val wrap        = address.burst === AxiBurstType.BURST_WRAP
    val legalLength = (!fixed || address.len < 16.U) &&
      (!wrap || Seq(1, 3, 7, 15).map(length => address.len === length.U).reduce(_ || _))
    val legalBurst = (fixed || incr || wrap) && legalLength
    val legalSize  = address.size.asUInt <= params.fullSize.U

    // Extend the calculation so an overflow cannot silently become a valid address.
    val width        = math.max(params.addrWidth + 1, 16)
    val start        = address.addr.pad(width)
    val transferMask = ((1.U(8.W) << address.size.asUInt) - 1.U).pad(width)
    val alignedStart = start & ~transferMask
    val span         = ((address.len +& 1.U) << address.size.asUInt).pad(width)
    val boundary     = alignedStart & ~(span - 1.U)
    val lastByte = Mux(fixed, alignedStart + transferMask, Mux(wrap, boundary + span - 1.U, alignedStart + span - 1.U))
    val wrapAligned = !wrap || (start & transferMask) === 0.U
    val withinPage  = (start >> 12) === (lastByte >> 12)
    val withinRange = lastByte < (BigInt(1) << params.addrWidth).U
    val fullWidth   = address.size.asUInt === params.fullSize.U
    val wordAligned = (address.addr & (params.strobeWidth - 1).U) === 0.U

    when(accepted) {
      assert(legalSize, "AXI transfer size exceeds the bus data width.")
      assert(legalBurst, "AXI burst type or length is invalid.")
      assert(wrapAligned, "AXI WRAP start address must be aligned to the transfer size.")
      assert(withinPage, "AXI burst crosses a 4KB boundary.")
    }
    legalSize && legalBurst && wrapAligned && withinPage && withinRange && fullWidth && wordAligned
  }
}
