package mylib

import chisel3._
import chipmunk._

class MyIncrement extends Module {
  val io = IO(new Bundle {
    val source = Input(UInt(2.W))
    val sink = Output(UInt(2.W))
  })

  io.sink := RegNext(io.source + 1.U, init = 0.U)
}

class MyChipTop extends RawModule {
  val coreClock  = IO(Input(Clock()))
  val coreReset  = IO(Input(AsyncReset()))
  val coreSource = IO(Input(UInt(2.W)))
  val coreSink   = IO(Output(UInt(2.W)))

  val clockSys = coreClock
  val resetSys = AsyncResetSyncDessert.withSpecificClockDomain(clockSys, coreReset)

  withClockAndReset(clockSys, resetSys) {
    val uIncrement = Module(new MyIncrement)
    uIncrement.io.source <> coreSource
    uIncrement.io.sink <> coreSink
  }

}

object RtlEmitter extends App {
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new MyChipTop,
    args ++ Array("--target-dir=generate", "--split-verilog")
  )
  println(">>> RTL emitted in \"generate\" directory.")
}
