package chipmunk
package tester

import chisel3.simulator._
import chisel3._
import svsim._

/** Configuration for [[TestRunner]]. You can initialize and run a simulation with this class.
  *
  * @example
  *   {{{
  * TestRunnerConfig(withWaveform = true).simulate(new Dut) { dut =>
  *   dut.clock.poke(true)
  *   // ... Add more testbench code here
  * }
  *   }}}
  * @param withWaveform
  *   Whether to dump simulation waveform or not. The default format is VCD for Verilator.
  * @param ephemeral
  *   Whether to delete the workspace directory after simulation is done.
  * @param testRunningPath
  *   The path to the directory where the simulation workspace is created.
  */
case class TestRunnerConfig(
  withWaveform: Boolean = false,
  ephemeral: Boolean = false,
  testRunningPath: String = "test_run"
) {
  def simulate[T <: RawModule](module: => T)(body: T => Unit): Unit = {
    val testRunner = new TestRunner(this)
    testRunner.simulate(module)(body)
  }
}

class TestRunner(config: TestRunnerConfig) extends SingleBackendSimulator[verilator.Backend] {
  val workspacePath: String =
    Seq(config.testRunningPath, getClass.getName.stripSuffix("$"), ProcessHandle.current().pid().toString).mkString("/")

  val backend = verilator.Backend.initializeFromProcessEnvironment()
  val commonCompilationSettings: CommonCompilationSettings = {
    import CommonCompilationSettings._
    CommonCompilationSettings(
      availableParallelism = AvailableParallelism.UpTo(Runtime.getRuntime.availableProcessors()),
      optimizationStyle = OptimizationStyle.OptimizeForCompilationSpeed
    )
  }
  val backendSpecificCompilationSettings: backend.CompilationSettings = {
    import verilator.Backend.CompilationSettings._
    verilator.Backend.CompilationSettings(traceStyle = if (config.withWaveform) Some(TraceStyle.Vcd()) else None)
  }
  val tag: String = "default"

  def simulate[T <: RawModule](module: => T)(body: T => Unit): Unit = {
    synchronized {
      super
        .simulate(module)({ module =>
          module.controller.setTraceEnabled(config.withWaveform)
          body(module.wrapped)
        })
        .result
    }
  }

  if (config.ephemeral) {
    // Try to clean up temporary workspace if possible when JVM exits
    sys.addShutdownHook {
      Runtime.getRuntime.exec(Array("rm", "-rf", workspacePath)).waitFor()
    }
  }
}
