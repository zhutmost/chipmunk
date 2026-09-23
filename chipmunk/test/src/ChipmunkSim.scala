package chipmunk.test

import chipmunk.tester.{MultiClockSupport, TesterAPI, TraceSupport}
import chisel3.simulator.scalatest.{ChiselSim, Cli}
import chisel3.simulator.scalatest.HasCliOptions.CliOption
import org.scalatest.TestSuite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

trait ChipmunkSim
    extends ChiselSim
    with Cli.Simulator
    with Cli.EmitFsdb
    with Cli.EmitVpd
    with TesterAPI
    with TraceSupport
    with MultiClockSupport:
  this: TestSuite =>

  addOption(
    CliOption.flag(
      name = "emitFst",
      help = "compile Verilator with FST waveform support and start dumping waves at time zero",
      updateCommonSettings = options =>
        options.copy(simulationSettings = options.simulationSettings.copy(enableWavesAtTimeZero = true)),
      updateBackendSettings = {
        case options: svsim.verilator.Backend.CompilationSettings =>
          options.withTraceStyle(Some(fstTraceStyle))

        case _: svsim.vcs.Backend.CompilationSettings =>
          throw new IllegalArgumentException(
            "VCS does not support FST waveforms; use -DemitFsdb=1, -DemitVpd=1, or -DemitVcd=1."
          )

        case other =>
          throw new IllegalArgumentException(s"${other.getClass.getName} does not support Chipmunk FST configuration.")
      }
    )
  )

abstract class ChipmunkFlatSpec extends AnyFlatSpec with Matchers with ChipmunkSim

abstract class ChipmunkFreeSpec extends AnyFreeSpec with Matchers with ChipmunkSim

abstract class ChipmunkFunSpec extends AnyFunSpec with Matchers with ChipmunkSim
