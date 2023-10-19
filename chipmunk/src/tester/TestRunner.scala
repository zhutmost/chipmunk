package chipmunk
package tester

import chisel3.simulator._
import chisel3._
import svsim._

/** Provides method to run simulation with Scala-written testbench.
  *
  * The simulation-related files will be stored in a [[Workspace]], whose default path is
  * `/test-run/$className/$processId`.
  *
  * This trait is backend-agnostic. End users should mix in a backend-specific sub-trait, such as
  * [[VerilatorTestRunner]]. To run simulation with a new backend, the method [[_createSimulation]] should be
  * implemented in sub-trait.
  *
  * @tparam B
  *   The backend type. It can be [[verilator.Backend]] or [[vcs.Backend]].
  */
trait TestRunner[B <: Backend] {

  /** The configuration for simulation.
    *
    * @param withWaveform
    *   Whether to enable waveform generation.
    */
  case class TestRunnerConfig(withWaveform: Boolean = false)

  private val workspacePath: String =
    Seq("test_run", getClass.getSimpleName.stripSuffix("$"), ProcessHandle.current().pid().toString).mkString("/")
  val workspace = new Workspace(workspacePath)

  /** Create a [[Simulation]] object with a specific backend according to the given config.
    *
    * This method should be implemented by a backend-specific sub-trait.
    *
    * @param config
    *   The configuration for the simulation.
    * @param workingDirTag
    *   The name suffix of the working directory during a certain simulation.
    */
  protected def _createSimulation(config: TestRunnerConfig, workingDirTag: String): Simulation

  implicit class TestRunnerConfigWrapper(config: TestRunnerConfig) {

    /** Elaborate the given module, and prepare the other necessary files in the workspace. It will return a
      * [[SimulationContext]] object, and you can call [[SimulationContext.runSim]] to run the simulation.
      *
      * This method will reset the [[workspace]] directory, and then generate other simulation-related files in the
      * workspace.
      *
      * @param module
      *   The module to be elaborated.
      * @param additionalVerilogResources
      *   Other Verilog resources required by the simulation.
      */
    def compile[T <: RawModule](module: => T, additionalVerilogResources: Seq[String] = Seq()): SimulationContext[T] = {
      workspace.reset()
      val elaboratedModule = workspace.elaborateGeneratedModule({ () => module })
      additionalVerilogResources.foreach(workspace.addPrimarySourceFromResource(getClass, _))
      workspace.generateAdditionalSources()

      val context = new SimulationContext(config, elaboratedModule)
      context
    }
  }

  /** Create a [[SimulationContext]] object with the default configuration. It is equivalent to calling
    * `TestRunnerConfig().compile(...)`.
    *
    * @see
    *   [[TestRunnerConfigWrapper.compile]]
    */
  def compile[T <: RawModule](module: => T, additionalVerilogResources: Seq[String] = Seq()): SimulationContext[T] =
    TestRunnerConfig().compile(module, additionalVerilogResources)

  class SimulationContext[T <: RawModule](val config: TestRunnerConfig, val elaboratedModule: ElaboratedModule[T]) {
    private var testCnt: Int = 0
    private def getTestCnt(inc: Boolean = true): Int = {
      if (inc) {
        testCnt += 1
      }
      testCnt
    }

    /** Run the simulation with the given Scala-written testbench.
      *
      * The simulation scripts and logs (and waveform if enabled) will be stored in the `$workspace/workdir-runSim-$id`.
      *
      * @param testbench
      *   The testbench to be run.
      * @example
      *   {{{
      * val compiled = TestRunnerConfig().compile(new MyModule())
      * compiled.runSim { dut =>
      *   import TestRunnerUtils._
      *   dut.clock.step()
      *   dut.io.a #= -1.S(3.W)
      *   dut.clock.step()
      *   dut.io.b expect -1
      * }
      *   }}}
      */
    def runSim(testbench: T => Unit): Unit = {
      val workingDirTag: String  = s"runSim-${getTestCnt()}"
      val simulation: Simulation = _createSimulation(config, workingDirTag)
      synchronized {
        simulation.runElaboratedModule(elaboratedModule) { module =>
          module.controller.setTraceEnabled(config.withWaveform)
          testbench(module.wrapped)
        }
      }
    }
  }
}
