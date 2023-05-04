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
  val coreClock  = Wire(Clock())
  val coreReset  = Wire(AsyncReset())
  val coreSource = Wire(UInt(2.W))
  val coreSink   = Wire(UInt(2.W))

  val clockSys = coreClock
  val resetSys = AsyncResetSyncDessert.withSpecificClockDomain(clockSys, coreReset)

  withClockAndReset(clockSys, resetSys) {
    val uIncrement = Module(new MyIncrement)
    uIncrement.io.source <> coreSource
    uIncrement.io.sink <> coreSink
  }

  import chipmunk.iocell.tsmc.TPHN28HPCPGV18._
  createInputIOCell(new PDUW08DGZ_V_G, "padClock")(coreClock, pullEnable = false.B)
  createInputIOCell(new PDUW08DGZ_V_G, "padSource")(coreSource, pullEnable = false.B)
  createInputIOCell(new PDUW08DGZ_V_G, "padReset")(coreReset, pullEnable = false.B)
  createOutputIOCell(new PDUW08DGZ_V_G, "padSink")(coreSink, outEnable = false.B, pullEnable = false.B)
}

object RtlEmitter extends App {
  ChiselStage.emitSystemVerilogFile(
    new MyChipTop,
    args ++ Array("--target-dir=generate", "--split-verilog")
  )
  println(">>> RTL emitted in \"generate\" directory.")
}
