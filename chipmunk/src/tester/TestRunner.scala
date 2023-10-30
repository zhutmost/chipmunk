package chipmunk
package tester

import chisel3._
import chisel3.simulator._
import svsim._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Provides method to run simulation with Scala-written testbench.
  *
  * The simulation-related files will be stored in a [[Workspace]], whose default path is
  * `generate/test/$className/$processId-$timestamp`.
  *
  * This trait is backend-agnostic. End users should mix in a backend-specific sub-trait, such as
  * [[VerilatorTestRunner]]. To run simulation with a new backend, the method [[_createSimulation]] should be
  * implemented in sub-trait.
  *
  * @tparam B
  *   The backend type. It can be [[verilator.Backend]] or [[vcs.Backend]].
  */
trait TestRunner[B <: Backend] {

  private val className: String = getClass.getSimpleName.stripSuffix("$")

  /** The configuration for simulation.
    *
    * @param withWaveform
    *   Whether to enable waveform generation.
    * @param testRunDirPath
    *   The path of the test running directory ("./generate/test" by default).
    */
  case class TestRunnerConfig(withWaveform: Boolean = false, testRunDirPath: String = "generate/test") {

    /** Elaborate the given module, and prepare the other necessary files in the workspace. It will return a
      * [[SimulationContext]] object, and you can call [[SimulationContext.runSim]] to run the simulation.
      *
      * This method will initialize a workspace directory, and then generate other simulation-related files in the it.
      *
      * @param module
      *   The module to be elaborated.
      * @param additionalVerilogResources
      *   Other Verilog resources required by the simulation.
      */
    def compile[T <: RawModule](module: => T, additionalVerilogResources: Seq[String] = Seq()): SimulationContext[T] = {
      val jvmPid: String        = ProcessHandle.current().pid().toString
      val timestamp: String     = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
      val workspacePath: String = Seq(testRunDirPath, className, s"$jvmPid-$timestamp").mkString("/")

      val workspace = new Workspace(workspacePath)
      workspace.reset()
      val elaboratedModule = workspace.elaborateGeneratedModule(() => module)
      additionalVerilogResources.foreach(workspace.addPrimarySourceFromResource(getClass, _))
      workspace.generateAdditionalSources()

      val context = new SimulationContext(this, workspace, elaboratedModule)
      context
    }
  }

  /** Create a [[SimulationContext]] object with the default [[TestRunnerConfig]] configuration. It is equivalent to
    * calling `TestRunnerConfig().compile(...)`.
    *
    * @see
    *   [[TestRunnerConfig.compile]]
    */
  def compile[T <: RawModule](module: => T, additionalVerilogResources: Seq[String] = Seq()): SimulationContext[T] =
    TestRunnerConfig().compile(module, additionalVerilogResources)

  /** Create a [[Simulation]] object with a specific backend according to the given config.
    *
    * This method should be implemented by a backend-specific sub-trait.
    *
    * @param config
    *   The configuration for the simulation.
    * @param workspace
    *   The workspace directory for the simulation.
    * @param workingDirTag
    *   The name suffix of the working directory during a certain simulation.
    */
  protected def _createSimulation(config: TestRunnerConfig, workspace: Workspace, workingDirTag: String): Simulation

  class SimulationContext[T <: RawModule](
    val config: TestRunnerConfig,
    val workspace: Workspace,
    val elaboratedModule: ElaboratedModule[T]
  ) {
    private var runSimCnt: Int = 0
    private def getRunSimCnt(inc: Boolean = true): Int = {
      if (inc) {
        runSimCnt += 1
      }
      runSimCnt
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
      val workingDirTag: String  = s"runSim-${getRunSimCnt()}"
      val simulation: Simulation = _createSimulation(config, workspace, workingDirTag)
      synchronized {
        simulation.runElaboratedModule(elaboratedModule) { module =>
          module.controller.setTraceEnabled(config.withWaveform)
          testbench(module.wrapped)
        }
      }
    }
  }
}
