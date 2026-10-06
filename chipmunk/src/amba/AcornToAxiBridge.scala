package chipmunk
package amba

import chisel3.*

import chipmunk.acorn.{AcornIO, AcornParams}

/** Convert Acorn word accesses to same-width, single-beat, non-exclusive AXI4 transactions.
  *
  * Each direction accepts one command and retains its payload until the AXI request has transferred. AW and W complete
  * independently. AXI responses are buffered and held until Acorn accepts them; a direction accepts its next command
  * only after that response transfers. Read and write directions operate independently.
  *
  * Unaligned commands return a local error without accessing AXI; rejected reads return zero. Write strobes, including
  * zero strobes, are forwarded unchanged. CACHE/QoS/REGION are zero; PROT and the fixed IDs are configured statically.
  * OKAY maps to success and every other response maps to an error. Unexpected IDs, missing RLAST, and EXOKAY are
  * checked by assertions. All endpoints share clock/reset; reset cancels outstanding transactions consistently.
  */
final class AcornToAxiBridge(
  val params: AxiParams,
  writeId: BigInt = 0,
  readId: BigInt = 0,
  writeProt: Int = 0,
  readProt: Int = 0,
) extends Module {
  require(params.addrWidth >= params.fullSize, "AXI address width must cover one full-width word.")
  require(writeId >= 0 && writeId.bitLength <= params.idWidth, "Write ID must fit the AXI ID width.")
  require(readId >= 0 && readId.bitLength <= params.idWidth, "Read ID must fit the AXI ID width.")
  require(writeProt >= 0 && writeProt < 8, "Write PROT must fit three bits.")
  require(readProt >= 0 && readProt < 8, "Read PROT must fit three bits.")

  private val acornParams = AcornParams(params.dataWidth, params.addrWidth)

  val io = IO(new Bundle {
    val sAcorn = Slave(new AcornIO(acornParams))
    val mAxi   = Master(new AxiIO(params))
  })

  private val readActive        = RegInit(false.B)
  private val arPending         = RegInit(false.B)
  private val readAddress       = Reg(UInt(params.addrWidth.W))
  private val readResponseValid = RegInit(false.B)
  private val readResponseData  = Reg(UInt(params.dataWidth.W))
  private val readError         = Reg(Bool())

  io.sAcorn.rd.cmd.ready := !readActive
  when(io.sAcorn.rd.cmd.fire) {
    val aligned = wordAligned(io.sAcorn.rd.cmd.bits.addr)
    readActive        := true.B
    arPending         := aligned
    readAddress       := io.sAcorn.rd.cmd.bits.addr
    readResponseValid := !aligned
    readResponseData  := 0.U
    readError         := !aligned
  }

  io.mAxi.ar.valid := readActive && arPending
  driveAddress(io.mAxi.ar.bits, readAddress, readId, readProt)
  when(io.mAxi.ar.fire) {
    arPending := false.B
  }

  io.mAxi.r.ready := readActive && !arPending && !readResponseValid
  when(io.mAxi.r.fire) {
    io.mAxi.r.bits.id.foreach(id => assert(id === readId.U, "Unexpected AXI read response ID."))
    assert(io.mAxi.r.bits.last, "Single-beat AXI reads must assert RLAST.")
    assert(io.mAxi.r.bits.resp =/= AxiResp.RESP_EXOKAY, "Normal AXI reads must not return EXOKAY.")
    readResponseValid := true.B
    readResponseData  := io.mAxi.r.bits.data
    readError         := io.mAxi.r.bits.resp =/= AxiResp.RESP_OKAY
  }

  io.sAcorn.rd.rsp.valid      := readResponseValid
  io.sAcorn.rd.rsp.bits.data  := readResponseData
  io.sAcorn.rd.rsp.bits.error := readError
  when(io.sAcorn.rd.rsp.fire) {
    readActive        := false.B
    readResponseValid := false.B
  }

  private val writeActive        = RegInit(false.B)
  private val awPending          = RegInit(false.B)
  private val wPending           = RegInit(false.B)
  private val writeAddress       = Reg(UInt(params.addrWidth.W))
  private val writeData          = Reg(UInt(params.dataWidth.W))
  private val writeStrobe        = Reg(UInt(params.strobeWidth.W))
  private val writeResponseValid = RegInit(false.B)
  private val writeError         = Reg(Bool())

  io.sAcorn.wr.cmd.ready := !writeActive
  when(io.sAcorn.wr.cmd.fire) {
    val aligned = wordAligned(io.sAcorn.wr.cmd.bits.addr)
    writeActive        := true.B
    awPending          := aligned
    wPending           := aligned
    writeAddress       := io.sAcorn.wr.cmd.bits.addr
    writeData          := io.sAcorn.wr.cmd.bits.data
    writeStrobe        := io.sAcorn.wr.cmd.bits.strobe
    writeResponseValid := !aligned
    writeError         := !aligned
  }

  io.mAxi.aw.valid := writeActive && awPending
  driveAddress(io.mAxi.aw.bits, writeAddress, writeId, writeProt)
  when(io.mAxi.aw.fire) {
    awPending := false.B
  }

  io.mAxi.w.valid     := writeActive && wPending
  io.mAxi.w.bits.data := writeData
  io.mAxi.w.bits.strb := writeStrobe
  io.mAxi.w.bits.last := true.B
  when(io.mAxi.w.fire) {
    wPending := false.B
  }

  io.mAxi.b.ready := writeActive && !awPending && !wPending && !writeResponseValid
  when(io.mAxi.b.fire) {
    io.mAxi.b.bits.id.foreach(id => assert(id === writeId.U, "Unexpected AXI write response ID."))
    assert(io.mAxi.b.bits.resp =/= AxiResp.RESP_EXOKAY, "Normal AXI writes must not return EXOKAY.")
    writeResponseValid := true.B
    writeError         := io.mAxi.b.bits.resp =/= AxiResp.RESP_OKAY
  }

  io.sAcorn.wr.rsp.valid      := writeResponseValid
  io.sAcorn.wr.rsp.bits.error := writeError
  when(io.sAcorn.wr.rsp.fire) {
    writeActive        := false.B
    writeResponseValid := false.B
  }

  private def wordAligned(address: UInt): Bool = (address & (params.strobeWidth - 1).U) === 0.U

  private def driveAddress(out: AxiWriteAddrChannel, address: UInt, id: BigInt, prot: Int): Unit = {
    out.addr  := address
    out.prot  := prot.U
    out.size  := AxiBurstSize(params.fullSize.U)
    out.len   := 0.U
    out.burst := AxiBurstType.BURST_INCR
    out.lock  := 0.U
    out.cache := 0.U
    out.id.foreach(_ := id.U(params.idWidth.W))
    out.qos.foreach(_ := 0.U)
    out.region.foreach(_ := 0.U)
  }
}
