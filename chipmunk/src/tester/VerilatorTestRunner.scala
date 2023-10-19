package chipmunk
package tester

import svsim._

/** Provides methods to run simulation in Scala with Verilator.
  *
  * Before using it, you should ensure that Verilator is installed and available in the environment.
  */
trait VerilatorTestRunner extends TestRunner[verilator.Backend] {
  def _createSimulation(config: TestRunnerConfig, workingDirTag: String): Simulation = {
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

    val simulation: Simulation =
      workspace.compile(backend)(
        workingDirectoryTag = workingDirTag,
        commonSettings = commonCompilationSettings,
        backendSpecificSettings = backendSpecificCompilationSettings,
        customSimulationWorkingDirectory = None,
        verbose = false
      )
    simulation
  }
}
