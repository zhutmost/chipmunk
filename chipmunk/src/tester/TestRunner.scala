package chipmunk
package tester

import chisel3._
import chisel3.simulator._
import svsim._

class SimulationContext[T <: RawModule](
  val simulation: Simulation,
  val workspace: Workspace,
  val elaboratedModule: ElaboratedModule[T]
) {
  def runSim[U](testbench: SimulatedModule[T] => U): Boolean = {
    try {
      simulation.runElaboratedModule(elaboratedModule) { testbench(_) }
      true
    } catch {
      case svsim.Simulation.UnexpectedEndOfMessages => false
    }
  }
}

trait TestRunner {
  def compileTester[T <: RawModule](module: => T, additionalVerilogResources: Seq[String] = Seq()): SimulationContext[T]
}
