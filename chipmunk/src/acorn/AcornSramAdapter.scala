package chipmunk
package acorn

import chisel3.*
import chisel3.util.{log2Ceil, Queue, ShiftRegister, SRAMInterface}

enum AcornSramPortMode {
  case SinglePort, OneReadOneWrite
}

/** SRAM geometry, fixed read latency, and response capacities. */
final case class AcornSramConfig(
  params: AcornParams,
  numWords: BigInt,
  portMode: AcornSramPortMode = AcornSramPortMode.SinglePort,
  readLatency: Int = 1,
  outstanding: AcornOutstanding = AcornOutstanding(),
) {
  require(numWords >= 1, "An Acorn SRAM needs at least one word.")
  require(readLatency >= 1, "SRAM read latency must be positive.")

  val sizeBytes: BigInt = numWords * params.bytesPerWord
  require(sizeBytes <= (BigInt(1) << params.addrWidth), "SRAM capacity exceeds the Acorn address range.")
}

/** Connect an Acorn slave to a synchronous, byte-masked 1RW or 1R1W SRAM.
  *
  * The SRAM must accept an enabled access each cycle, commit writes at that clock edge, and return each read's value
  * after exactly `readLatency` cycles. Read responses add one FIFO cycle. Conflicting reads/writes are arbitrated
  * round-robin; a 1R1W SRAM can access different words concurrently. Addresses are zero-based local byte offsets.
  * Unaligned/out-of-range accesses return errors without accessing SRAM. Zero-strobe writes succeed without writing.
  * Reset cancels outstanding transactions but does not clear SRAM. The upstream master must not issue commands during
  * reset; reset the connected Acorn routers consistently.
  */
final class AcornSramAdapter(val config: AcornSramConfig) extends Module {
  private val params     = config.params
  private val singlePort = config.portMode == AcornSramPortMode.SinglePort

  val io = IO(new Bundle {
    val access = Slave(new AcornIO(params))
    val sram   = Flipped(
      new SRAMInterface(
        memSize = config.numWords,
        tpe = Vec(params.bytesPerWord, UInt(8.W)),
        numReadPorts = if (singlePort) 0 else 1,
        numWritePorts = if (singlePort) 0 else 1,
        numReadwritePorts = if (singlePort) 1 else 0,
        masked = true,
      )
    )
  })

  private val readResponses = Module(
    new Queue(new AcornRdRspChannel(params.dataWidth), entries = config.outstanding.read, pipe = false, flow = false)
  )
  private val writeResponses = Module(
    new Queue(new AcornWrRspChannel, entries = config.outstanding.write, pipe = false, flow = false)
  )

  io.access.rd.rsp.valid     := readResponses.io.deq.valid
  io.access.rd.rsp.bits      := readResponses.io.deq.bits
  readResponses.io.deq.ready := io.access.rd.rsp.ready

  io.access.wr.rsp.valid      := writeResponses.io.deq.valid
  io.access.wr.rsp.bits       := writeResponses.io.deq.bits
  writeResponses.io.deq.ready := io.access.wr.rsp.ready

  // Count both pending SRAM results and buffered responses before issuing another read.
  private val readCount  = RegInit(0.U(log2Ceil(BigInt(config.outstanding.read) + 1).W))
  private val readSpace  = readCount < config.outstanding.read.U
  private val writeSpace = writeResponses.io.enq.ready

  private def addressError(address: UInt): Bool = {
    val aligned = (address & (params.bytesPerWord - 1).U) === 0.U
    !aligned || address >= config.sizeBytes.U
  }

  private val readError      = addressError(io.access.rd.cmd.bits.addr)
  private val writeError     = addressError(io.access.wr.cmd.bits.addr)
  private val readWord       = io.access.rd.cmd.bits.addr >> log2Ceil(params.bytesPerWord)
  private val writeWord      = io.access.wr.cmd.bits.addr >> log2Ceil(params.bytesPerWord)
  private val readNeedsSram  = !readError
  private val writeNeedsSram = !writeError && io.access.wr.cmd.bits.strobe.orR

  private val readCandidate  = readSpace && io.access.rd.cmd.valid
  private val writeCandidate = writeSpace && io.access.wr.cmd.valid
  private val samePortOrWord = if (singlePort) true.B else readWord === writeWord
  private val conflict       = readCandidate && writeCandidate && readNeedsSram && writeNeedsSram && samePortOrWord
  private val preferWrite    = RegInit(false.B)

  io.access.rd.cmd.ready := readSpace && (!conflict || !preferWrite)
  io.access.wr.cmd.ready := writeSpace && (!conflict || preferWrite)

  when(conflict && (io.access.rd.cmd.fire || io.access.wr.cmd.fire)) {
    preferWrite := !preferWrite
  }

  private val doRead    = io.access.rd.cmd.fire && readNeedsSram
  private val doWrite   = io.access.wr.cmd.fire && writeNeedsSram
  private val writeData = io.access.wr.cmd.bits.data.asTypeOf(Vec(params.bytesPerWord, UInt(8.W)))
  private val writeMask = VecInit(io.access.wr.cmd.bits.strobe.asBools)
  private val readData  = Wire(UInt(params.dataWidth.W))

  if (singlePort) {
    val port = io.sram.readwritePorts(0)
    port.enable    := doRead || doWrite
    port.isWrite   := doWrite
    port.address   := Mux(doWrite, writeWord, readWord)
    port.writeData := writeData
    port.mask.get  := writeMask
    readData       := port.readData.asUInt
  } else {
    val readPort  = io.sram.readPorts(0)
    val writePort = io.sram.writePorts(0)
    readPort.enable    := doRead
    readPort.address   := readWord
    writePort.enable   := doWrite
    writePort.address  := writeWord
    writePort.data     := writeData
    writePort.mask.get := writeMask
    readData           := readPort.data.asUInt
  }

  // Error reads use the same latency pipeline so they cannot overtake valid reads.
  private val readReturned  = ShiftRegister(io.access.rd.cmd.fire, config.readLatency, false.B, true.B)
  private val returnedError = ShiftRegister(readError, config.readLatency, false.B, true.B)

  readResponses.io.enq.valid      := readReturned
  readResponses.io.enq.bits.data  := Mux(returnedError, 0.U, readData)
  readResponses.io.enq.bits.error := returnedError

  writeResponses.io.enq.valid      := io.access.wr.cmd.fire
  writeResponses.io.enq.bits.error := writeError

  when(io.access.rd.cmd.fire =/= io.access.rd.rsp.fire) {
    when(io.access.rd.cmd.fire) {
      readCount := readCount + 1.U
    }.otherwise {
      readCount := readCount - 1.U
    }
  }

  assert(!readResponses.io.enq.valid || readResponses.io.enq.ready, "SRAM read response has no reserved space.")
}
