package chipmunk
package test

import chisel3.simulator.scalatest.{ChiselSim, Cli, HasCliOptions}
import chisel3.simulator.scalatest.HasCliOptions.CliOption
import org.scalatest.TestSuite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import chipmunk.tester.{MultiClockSupport, TesterAPI}

trait EmitFst {
  this: HasCliOptions =>

  addOption(
    CliOption.flag(
      name = "emitFst",
      help = "compile Verilator with FST waveform support and start dumping waves at time zero",
      updateCommonSettings = options =>
        options.copy(simulationSettings = options.simulationSettings.copy(enableWavesAtTimeZero = true)),
      updateBackendSettings = {
        case options: svsim.verilator.Backend.CompilationSettings =>
          options.withTraceStyle(
            Some(
              svsim.verilator.Backend.CompilationSettings
                .TraceStyle(kind = svsim.verilator.Backend.CompilationSettings.TraceKind.Fst())
            )
          )

        case _: svsim.vcs.Backend.CompilationSettings =>
          throw new IllegalArgumentException("VCS does not support FST; use FSDB, VPD, or VCD.")

        case other =>
          throw new IllegalArgumentException(s"${other.getClass.getName} does not support FST.")
      },
    )
  )
}

trait ChipmunkSim
    extends ChiselSim
    with Cli.Simulator
    with Cli.EmitFsdb
    with Cli.EmitVpd
    with EmitFst
    with TesterAPI
    with MultiClockSupport {
  this: TestSuite =>
}

abstract class ChipmunkFlatSpec extends AnyFlatSpec with Matchers with ChipmunkSim

abstract class ChipmunkFreeSpec extends AnyFreeSpec with Matchers with ChipmunkSim

abstract class ChipmunkFunSpec extends AnyFunSpec with Matchers with ChipmunkSim
