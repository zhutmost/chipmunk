package chipmunk.test

import chipmunk.tester.SimulationArtifacts
import chisel3.*
import chisel3.simulator.HasSimulator

import java.nio.file.Files

private object TraceSpecDut:
  final class Harness extends Module:
    val io = IO(new Bundle:
      val input  = Input(UInt(8.W))
      val output = Output(UInt(8.W)))

    val sampled = RegInit(0.U(8.W))
    sampled   := io.input
    io.output := sampled

class TraceSpec extends ChipmunkFlatSpec {
  "TraceSupport" should "write a non-empty FST for an explicitly enabled waveform window" in {
    given fstSimulator: HasSimulator = HasSimulator.simulators.verilator(verilatorSettings =
      svsim.verilator.Backend.CompilationSettings.default.withTraceStyle(Some(fstTraceStyle))
    )

    simulate(new TraceSpecDut.Harness, subdirectory = Some("fst-window")): dut =>
      dut.io.input #= 0x11.U
      dut.clock.step()
      dut.io.output.expect(0x11.U)

      val result = withWaves:
        for value <- Seq(0x22, 0x33, 0x44) do
          dut.io.input #= value.U
          dut.clock.step()
          dut.io.output.expect(value.U)
        "window-complete"

      result shouldBe "window-complete"

      // Activity outside this block must remain legal after withWaves has disabled tracing.
      dut.io.input #= 0x55.U
      dut.clock.step()
      dut.io.output.expect(0x55.U)

    val fstFiles = SimulationArtifacts.traceFiles.filter(_.getFileName.toString.endsWith(".fst"))
    withClue(s"trace files: ${SimulationArtifacts.traceFiles.mkString(", ")}"):
      fstFiles should not be empty
    fstFiles.foreach(path => Files.size(path) should be > 1L)

    SimulationArtifacts.logFiles should not be empty
    SimulationArtifacts.replayScripts should not be empty
  }
}
