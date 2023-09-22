package chipmunk
package tester

import chisel3._
import chisel3.simulator._
import svsim._

import java.text.SimpleDateFormat
import java.util.Calendar

trait VerilatorTestRunner extends TestRunner {
  private val verilatorBackend = verilator.Backend.initializeFromProcessEnvironment()
  private val timeStampFormat  = new SimpleDateFormat("yyyyMMddHHmmss")

  def compileTester[T <: RawModule](
    module: => T,
    additionalVerilogResources: Seq[String] = Seq()
  ): VerilatorSimulationContext[T] = {
    val workspacePath = Seq(
      "test_run",
      // This is taken from the legacy `TesterDriver` class. It isn't ideal and we hope to improve this eventually.
      timeStampFormat.format(Calendar.getInstance().getTime),
      getClass.getSimpleName
    ).mkString("/")
    val workspace = new Workspace(workspacePath)
    workspace.reset()
    val elaboratedModule = workspace.elaborateGeneratedModule({ () => module })
    additionalVerilogResources.foreach(workspace.addPrimarySourceFromResource(getClass, _))
    workspace.generateAdditionalSources()
    val simulation = workspace.compile(verilatorBackend)(
      "verilator", {
        import CommonCompilationSettings._
        CommonCompilationSettings(
          availableParallelism = AvailableParallelism.UpTo(Runtime.getRuntime.availableProcessors()),
          optimizationStyle = OptimizationStyle.OptimizeForCompilationSpeed,
          verilogPreprocessorDefines = Seq(
            VerilogPreprocessorDefine("ASSERT_VERBOSE_COND", s"!${Workspace.testbenchModuleName}.reset"),
            VerilogPreprocessorDefine("PRINTF_COND", s"!${Workspace.testbenchModuleName}.reset"),
            VerilogPreprocessorDefine("STOP_COND", s"!${Workspace.testbenchModuleName}.reset")
          )
        )
      },
      verilator.Backend
        .CompilationSettings(disabledWarnings = Seq("WIDTH", "STMTDLY"), disableFatalExitOnWarnings = true),
      customSimulationWorkingDirectory = None,
      verbose = false
    )
    val context = new VerilatorSimulationContext(simulation, workspace, elaboratedModule)
    context
  }
}

class VerilatorSimulationContext[T <: RawModule](
  simulation: Simulation,
  workspace: Workspace,
  elaboratedModule: ElaboratedModule[T]
) extends SimulationContext(simulation, workspace, elaboratedModule) {
  override def runSim[U](testbench: SimulatedModule[T] => U): Boolean = {
    val success =
      try {
        simulation
          .runElaboratedModule(elaboratedModule) { module =>
            testbench(module)
          }
        true
      } catch {
        // We eventually want to have a more structured way of detecting assertions, but this works for now.
        case svsim.Simulation.UnexpectedEndOfMessages =>
          val filename   = s"${workspace.absolutePath}/workdir-verilator/simulation-log.txt"
          val sourceFile = scala.io.Source.fromFile(filename)
          for (line <- sourceFile.getLines()) {
            if (line.contains("Verilog $finish")) {
              // We don't immediately exit on $finish, so we need to ignore assertions that happen after a call to
              // $finish
              return true
            }
            if (line.contains("Assertion failed")) {
              return false
            }
          }
          true
      }
    success
  }
}
