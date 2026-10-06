package chipmunk
package acorn

import chisel3.*
import chisel3.util.log2Ceil

/** Convert naturally aligned Acorn words while preserving byte addresses and lane order.
  *
  * Unaligned commands return a local error without accessing the endpoint; error reads return zero.
  *
  * The endpoint must permit the expanded read footprint or non-atomic split reads, as applicable, with no read side
  * effects. Writes require byte-strobe semantics independent of access width and permission for non-atomic splitting.
  * The entire converted word must lie within the authorized endpoint region. These are integration requirements, not
  * properties the adapter can discover or enforce from Acorn commands.
  *
  * Narrow-to-wide accesses select one lane of a wider word. Wide-to-narrow accesses visit all subwords in increasing
  * address order, including zero-strobe writes, and stop at the first error. Earlier writes are not rolled back. No
  * read-modify-write, request merging, or reuse of earlier read data is performed. Read/write directions are
  * independent, with one command each until its upstream response transfers. Endpoint requests and upstream responses
  * are buffered; all connected components share clock/reset and must cancel outstanding transactions consistently on
  * reset.
  */
final class AcornWidthAdapter(val inputParams: AcornParams, val outputParams: AcornParams) extends Module {
  require(inputParams.addrWidth == outputParams.addrWidth, "Width adaptation must preserve the byte-address width.")
  require(
    inputParams.addrWidth >= log2Ceil(math.max(inputParams.bytesPerWord, outputParams.bytesPerWord)),
    "The address width must cover the larger word.",
  )

  private val split     = inputParams.dataWidth > outputParams.dataWidth
  private val expand    = inputParams.dataWidth < outputParams.dataWidth
  private val segments  = if (split) inputParams.dataWidth / outputParams.dataWidth else 1
  private val indexBits = math.max(1, log2Ceil(segments))
  private val addrMask  = ((BigInt(1) << outputParams.addrWidth) - outputParams.bytesPerWord).U

  val io = IO(new Bundle {
    val sAcorn = Slave(new AcornIO(inputParams))
    val mAcorn = Master(new AcornIO(outputParams))
  })

  private object Phase extends ChiselEnum {
    val Idle, Send, Wait, Respond = Value
  }

  private val readPhase   = RegInit(Phase.Idle)
  private val readAddress = Reg(UInt(inputParams.addrWidth.W))
  private val readIndex   = Reg(UInt(indexBits.W))
  private val readData    = Reg(UInt(inputParams.dataWidth.W))
  private val readError   = Reg(Bool())

  io.sAcorn.rd.cmd.ready := readPhase === Phase.Idle
  when(io.sAcorn.rd.cmd.fire) {
    val invalid = !aligned(io.sAcorn.rd.cmd.bits.addr)
    readAddress := io.sAcorn.rd.cmd.bits.addr
    readIndex   := 0.U
    readData    := 0.U
    readError   := invalid
    readPhase   := Mux(invalid, Phase.Respond, Phase.Send)
  }

  io.mAcorn.rd.cmd.valid     := readPhase === Phase.Send
  io.mAcorn.rd.cmd.bits.addr := endpointAddress(readAddress, readIndex)
  when(io.mAcorn.rd.cmd.fire) {
    readPhase := Phase.Wait
  }

  io.mAcorn.rd.rsp.ready := readPhase === Phase.Wait
  when(io.mAcorn.rd.rsp.fire) {
    val returned = if (split) {
      readData | (io.mAcorn.rd.rsp.bits.data << bitOffset(readIndex))(inputParams.dataWidth - 1, 0)
    } else if (expand) {
      (io.mAcorn.rd.rsp.bits.data >> laneOffset(readAddress))(inputParams.dataWidth - 1, 0)
    } else {
      io.mAcorn.rd.rsp.bits.data
    }
    readData  := Mux(io.mAcorn.rd.rsp.bits.error, 0.U, returned)
    readError := io.mAcorn.rd.rsp.bits.error
    when(io.mAcorn.rd.rsp.bits.error || readIndex === (segments - 1).U) {
      readPhase := Phase.Respond
    }.otherwise {
      readIndex := readIndex + 1.U
      readPhase := Phase.Send
    }
  }

  io.sAcorn.rd.rsp.valid      := readPhase === Phase.Respond
  io.sAcorn.rd.rsp.bits.data  := readData
  io.sAcorn.rd.rsp.bits.error := readError
  when(io.sAcorn.rd.rsp.fire) {
    readPhase := Phase.Idle
  }

  private val writePhase   = RegInit(Phase.Idle)
  private val writeAddress = Reg(UInt(inputParams.addrWidth.W))
  private val writeIndex   = Reg(UInt(indexBits.W))
  private val writeData    = Reg(UInt(inputParams.dataWidth.W))
  private val writeStrobe  = Reg(UInt(inputParams.bytesPerWord.W))
  private val writeError   = Reg(Bool())

  io.sAcorn.wr.cmd.ready := writePhase === Phase.Idle
  when(io.sAcorn.wr.cmd.fire) {
    val invalid = !aligned(io.sAcorn.wr.cmd.bits.addr)
    writeAddress := io.sAcorn.wr.cmd.bits.addr
    writeIndex   := 0.U
    writeData    := io.sAcorn.wr.cmd.bits.data
    writeStrobe  := io.sAcorn.wr.cmd.bits.strobe
    writeError   := invalid
    writePhase   := Mux(invalid, Phase.Respond, Phase.Send)
  }

  io.mAcorn.wr.cmd.valid     := writePhase === Phase.Send
  io.mAcorn.wr.cmd.bits.addr := endpointAddress(writeAddress, writeIndex)
  if (split) {
    io.mAcorn.wr.cmd.bits.data   := (writeData >> bitOffset(writeIndex))(outputParams.dataWidth - 1, 0)
    io.mAcorn.wr.cmd.bits.strobe :=
      (writeStrobe >> (writeIndex << log2Ceil(outputParams.bytesPerWord)))(outputParams.bytesPerWord - 1, 0)
  } else if (expand) {
    io.mAcorn.wr.cmd.bits.data   := (writeData << laneOffset(writeAddress))(outputParams.dataWidth - 1, 0)
    io.mAcorn.wr.cmd.bits.strobe :=
      (writeStrobe << writeAddress(log2Ceil(outputParams.bytesPerWord) - 1, 0))(outputParams.bytesPerWord - 1, 0)
  } else {
    io.mAcorn.wr.cmd.bits.data   := writeData
    io.mAcorn.wr.cmd.bits.strobe := writeStrobe
  }
  when(io.mAcorn.wr.cmd.fire) {
    writePhase := Phase.Wait
  }

  io.mAcorn.wr.rsp.ready := writePhase === Phase.Wait
  when(io.mAcorn.wr.rsp.fire) {
    writeError := io.mAcorn.wr.rsp.bits.error
    when(io.mAcorn.wr.rsp.bits.error || writeIndex === (segments - 1).U) {
      writePhase := Phase.Respond
    }.otherwise {
      writeIndex := writeIndex + 1.U
      writePhase := Phase.Send
    }
  }

  io.sAcorn.wr.rsp.valid      := writePhase === Phase.Respond
  io.sAcorn.wr.rsp.bits.error := writeError
  when(io.sAcorn.wr.rsp.fire) {
    writePhase := Phase.Idle
  }

  private def aligned(address: UInt): Bool = (address & (inputParams.bytesPerWord - 1).U) === 0.U

  private def endpointAddress(address: UInt, index: UInt): UInt =
    if (split) address + (index << log2Ceil(outputParams.bytesPerWord)) else address & addrMask

  private def bitOffset(index: UInt): UInt = index << log2Ceil(outputParams.dataWidth)

  private def laneOffset(address: UInt): UInt = address(log2Ceil(outputParams.bytesPerWord) - 1, 0) << 3
}
