package mylib

import chipmunk._
import chisel3._
import circt.stage._

class MyIncrement extends Module {
  val io = IO(new Bundle {
    val source = Input(UInt(3.W))
    val sink   = Output(UInt(3.W))
  })
  io.sink := RegNext(io.source + 1.U, init = 0.U)
}

class MyChipTop extends RawModule {
  val coreClock  = IO(Input(Clock()))
  val coreReset  = IO(Input(AsyncReset()))
  val coreSource = IO(Input(SInt(3.W)))
  val coreSink   = IO(Output(UInt(3.W)))

  implicit val clockSys: Clock      = coreClock
  implicit val resetSys: AsyncReset = AsyncResetSyncDessert.withSpecificClockDomain(clockSys, coreReset)

  withClockAndReset(clockSys, resetSys) {
    val uIncrement = Module(new MyIncrement)
    uIncrement.io.source <> coreSource.asUInt
    uIncrement.io.sink <> coreSink
  }
}

object RtlEmitter extends App {
  val targetDir = "generate/hw"

  val chiselArgs = Array(f"--target-dir=$targetDir", "--split-verilog")
  val firtoolOpts =
    Array("--disable-all-randomization", "-repl-seq-mem", f"-repl-seq-mem-file=seq-mem.conf")

  ChiselStage
    .emitSystemVerilogFile(new MyChipTop, chiselArgs, firtoolOpts)
  println(f">>> RTL emitted in \"$targetDir\" directory.")
}
