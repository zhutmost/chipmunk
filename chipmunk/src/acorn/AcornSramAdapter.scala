package chipmunk
package acorn

import chisel3.*
import chisel3.util.{log2Ceil, Queue, ShiftRegister, SRAMInterface}

enum AcornSramPortMode {
  case SinglePort, OneReadOneWrite
}

/** SRAM geometry, fixed read latency, and outstanding transaction capacities. */
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
  * Two-entry read/write command queues isolate the bus from SRAM control, without combinational bypass or ready
  * propagation. A command takes at least one cycle to reach SRAM; each port can still sustain one access per cycle. The
  * SRAM must accept an enabled access each cycle, commit writes at that clock edge, and return each read's value after
  * exactly `readLatency` cycles. Read responses add one FIFO cycle. Conflicting reads/writes are arbitrated
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

  // Reserve response capacity at bus acceptance, including commands still buffered here.
  private val readCount  = RegInit(0.U(log2Ceil(BigInt(config.outstanding.read) + 1).W))
  private val writeCount = RegInit(0.U(log2Ceil(BigInt(config.outstanding.write) + 1).W))
  private val readSpace  = readCount < config.outstanding.read.U
  private val writeSpace = writeCount < config.outstanding.write.U

  private val readCommands = Module(
    new Queue(new AcornRdCmdChannel(params.addrWidth), entries = 2, pipe = false, flow = false)
  )
  private val writeCommands = Module(
    new Queue(new AcornWrCmdChannel(params.dataWidth, params.addrWidth), entries = 2, pipe = false, flow = false)
  )
  readCommands.io.enq.valid  := io.access.rd.cmd.valid && readSpace
  readCommands.io.enq.bits   := io.access.rd.cmd.bits
  io.access.rd.cmd.ready     := readCommands.io.enq.ready && readSpace
  writeCommands.io.enq.valid := io.access.wr.cmd.valid && writeSpace
  writeCommands.io.enq.bits  := io.access.wr.cmd.bits
  io.access.wr.cmd.ready     := writeCommands.io.enq.ready && writeSpace

  private val readCommand  = readCommands.io.deq
  private val writeCommand = writeCommands.io.deq

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

  private def addressError(address: UInt): Bool = {
    val aligned = (address & (params.bytesPerWord - 1).U) === 0.U
    !aligned || address >= config.sizeBytes.U
  }

  private val readError      = addressError(readCommand.bits.addr)
  private val writeError     = addressError(writeCommand.bits.addr)
  private val readWord       = readCommand.bits.addr >> log2Ceil(params.bytesPerWord)
  private val writeWord      = writeCommand.bits.addr >> log2Ceil(params.bytesPerWord)
  private val readNeedsSram  = !readError
  private val writeNeedsSram = !writeError && writeCommand.bits.strobe.orR

  private val readCandidate  = readCommand.valid
  private val writeCandidate = writeResponses.io.enq.ready && writeCommand.valid
  private val samePortOrWord = if (singlePort) true.B else readWord === writeWord
  private val conflict       = readCandidate && writeCandidate && readNeedsSram && writeNeedsSram && samePortOrWord
  private val preferWrite    = RegInit(false.B)

  readCommand.ready  := !conflict || !preferWrite
  writeCommand.ready := writeResponses.io.enq.ready && (!conflict || preferWrite)

  when(conflict && (readCommand.fire || writeCommand.fire)) {
    preferWrite := !preferWrite
  }

  private val doRead    = readCommand.fire && readNeedsSram
  private val doWrite   = writeCommand.fire && writeNeedsSram
  private val writeData = writeCommand.bits.data.asTypeOf(Vec(params.bytesPerWord, UInt(8.W)))
  private val writeMask = VecInit(writeCommand.bits.strobe.asBools)
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
  private val readReturned  = ShiftRegister(readCommand.fire, config.readLatency, false.B, true.B)
  private val returnedError = ShiftRegister(readError, config.readLatency, false.B, true.B)

  readResponses.io.enq.valid      := readReturned
  readResponses.io.enq.bits.data  := Mux(returnedError, 0.U, readData)
  readResponses.io.enq.bits.error := returnedError

  writeResponses.io.enq.valid      := writeCommand.fire
  writeResponses.io.enq.bits.error := writeError

  when(io.access.rd.cmd.fire =/= io.access.rd.rsp.fire) {
    when(io.access.rd.cmd.fire) {
      readCount := readCount + 1.U
    }.otherwise {
      readCount := readCount - 1.U
    }
  }

  when(io.access.wr.cmd.fire =/= io.access.wr.rsp.fire) {
    when(io.access.wr.cmd.fire) {
      writeCount := writeCount + 1.U
    }.otherwise {
      writeCount := writeCount - 1.U
    }
  }

  assert(!readResponses.io.enq.valid || readResponses.io.enq.ready, "SRAM read response has no reserved space.")
}
