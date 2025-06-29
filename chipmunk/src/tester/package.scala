package chipmunk

import chisel3._
import chisel3.reflect.DataMirror
import svsim._

package tester {

  /** An opaque class that can be passed to `Simulation.run` to get access to a `SimulatedModule` in the simulation
    * body.
    */
  final class ElaboratedModule[T] private[tester] (
    private[tester] val wrapped: T,
    private[tester] val ports: Seq[(Data, ModuleInfo.Port)]
  )

  /** A class that enables using a Chisel module to control an `svsim.Simulation`.
    */
  final class SimulatedModule[T] private[tester] (
    private[tester] val elaboratedModule: ElaboratedModule[T],
    controller: Simulation.Controller
  ) extends AnySimulatedModule(elaboratedModule.ports, controller) {
    def wrapped: T = elaboratedModule.wrapped
  }

  sealed class AnySimulatedModule protected (
    ports: Seq[(Data, ModuleInfo.Port)],
    val controller: Simulation.Controller
  ) {

    // -- Port Mapping

    private val simulationPorts = ports.map { case (data, port) =>
      data -> controller.port(port.name)
    }.toMap

    def port(data: Data): Simulation.Port = {
      simulationPorts(data)
    }

    // -- Peek/Poke API Support

    // When using the low-level API, the user must explicitly call `controller.completeInFlightCommands()` to ensure
    // that all commands are executed. When using a higher-level API like peek/poke, we handle this automatically.
    private var shouldCompleteInFlightCommands: Boolean = false

    private[tester] def completeSimulation(): Unit = {
      if (shouldCompleteInFlightCommands) {
        shouldCompleteInFlightCommands = false
        controller.completeInFlightCommands()
      }
    }

    // The peek/poke API implicitly evaluates on the first peek after one or more pokes. This is _only_ for peek/poke
    // and using `controller` directly will not provide this behavior.
    private var evaluateBeforeNextPeek: Boolean = false

    private[tester] def willEvaluate(): Unit = {
      evaluateBeforeNextPeek = false
    }

    private[tester] def willPoke(): Unit = {
      shouldCompleteInFlightCommands = true
      evaluateBeforeNextPeek = true
    }

    private[tester] def willPeek(): Unit = {
      shouldCompleteInFlightCommands = true
      if (evaluateBeforeNextPeek) {
        willEvaluate()
        controller.run(0)
      }
    }
  }

}

package object tester {

  private[tester] object AnySimulatedModule {
    private val dynamicVariable = new scala.util.DynamicVariable[Option[AnySimulatedModule]](None)

    def withValue[T](module: AnySimulatedModule)(body: => T): T = {
      require(dynamicVariable.value.isEmpty, "Nested simulations are not supported.")
      dynamicVariable.withValue(Some(module))(body)
    }

    def current: AnySimulatedModule = dynamicVariable.value.get
  }

  implicit class ChipmunkWorkspace(workspace: Workspace) {
    def elaborateGeneratedModule[T <: RawModule](generateModule: () => T): ElaboratedModule[T] = {
      // Use CIRCT to generate SystemVerilog sources, and potentially additional artifacts
      var someDut: Option[T] = None
      (new circt.stage.ChiselStage).execute(
        Array("--target", "systemverilog", "--split-verilog"),
        Seq(
          chisel3.stage.ChiselGeneratorAnnotation { () =>
            val dut = generateModule()
            someDut = Some(dut)
            dut
          },
          circt.stage.FirtoolOption("-disable-annotation-unknown"),
          firrtl.options.TargetDirAnnotation(workspace.supportArtifactsPath)
        )
      )

      // Move the relevant files over to primary-sources
      val fileList =
        new java.io.BufferedReader(new java.io.FileReader(s"${workspace.supportArtifactsPath}/filelist.f"))
      try {
        fileList.lines().forEach { immutableFilename =>
          var filename = immutableFilename
          // Some files are provided as absolute paths
          if (filename.startsWith(workspace.supportArtifactsPath)) {
            filename = filename.substring(workspace.supportArtifactsPath.length + 1)
          }
          java.nio.file.Files.move(
            java.nio.file.Paths.get(s"${workspace.supportArtifactsPath}/$filename"),
            java.nio.file.Paths.get(s"${workspace.primarySourcesPath}/$filename")
          )
        }
      } finally {
        fileList.close()
      }

      // Initialize Module Info
      val dut   = someDut.get
      val ports = {

        // TODO: We infer the names of various ports since we don't currently have a good alternative when using MFC. We
        //       hope to replace this once we get better support from CIRCT.
        def leafPorts(node: Data, name: String): Seq[(Data, ModuleInfo.Port)] = {
          node match {
            case record: Record =>
              record.elements.toSeq.flatMap { case (fieldName, field) =>
                leafPorts(field, s"${name}_$fieldName")
              }
            case vec: Vec[_] =>
              vec.zipWithIndex.flatMap { case (element, index) =>
                leafPorts(element, s"${name}_$index")
              }
            case element: Element =>
              DataMirror.directionOf(element) match {
                case ActualDirection.Input =>
                  Seq((element, ModuleInfo.Port(name, isGettable = true, isSettable = true)))
                case ActualDirection.Output => Seq((element, ModuleInfo.Port(name, isGettable = true)))
                case _                      => Seq()
              }
            case _ => Seq()
          }
        }
        // Chisel ports can be Data or Property, but there is no ABI for Property ports, so we only return Data.
        DataMirror.modulePorts(dut).flatMap {
          case (name, data: Data) => leafPorts(data, name)
          case _                  => Nil
        }
      }
      workspace.elaborate(ModuleInfo(name = dut.name, ports = ports.map(_._2)))
      new ElaboratedModule(dut, ports)
    }
  }
}
