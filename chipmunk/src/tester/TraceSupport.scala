package chipmunk
package tester

import chisel3.simulator.ControlAPI
import chisel3.testing.HasTestingDirectory

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

trait TraceSupport:
  this: ControlAPI =>

  protected def fstTraceStyle: svsim.verilator.Backend.CompilationSettings.TraceStyle =
    svsim.verilator.Backend.CompilationSettings
      .TraceStyle(kind = svsim.verilator.Backend.CompilationSettings.TraceKind.Fst())

  final def withWaves[A](body: => A): A =
    enableWaves()
    var primaryFailure = Option.empty[Throwable]

    try body
    catch
      case error: Throwable =>
        primaryFailure = Some(error)
        throw error
    finally
      try disableWaves()
      catch
        case error: Throwable =>
          primaryFailure match
            case Some(primary) => primary.addSuppressed(error)
            case None          => throw error

/** Backend-neutral access to files produced for the current ScalaTest test. */
object SimulationArtifacts:

  private val traceFileExtensions = Set("fst", "vcd", "vpd", "fsdb")
  private val logFileNames        = Set("compilation-log.txt", "simulation-log.txt")
  private val replayFileNames     = Set("execution-script.txt")

  /** Root directory assigned by ChiselSim to the current ScalaTest test. */
  def testDirectory(using testingDirectory: HasTestingDirectory): Path = testingDirectory.getDirectory

  /** Waveform files below the current test directory, independent of simulator backend. */
  def traceFiles(using testingDirectory: HasTestingDirectory): Seq[Path] =
    findFiles(path =>
      val fileName = path.getFileName.toString
      traceFileExtensions.exists(extension => fileName.endsWith(s".$extension"))
    )

  /** Compilation and simulation logs below the current test directory. */
  def logFiles(using testingDirectory: HasTestingDirectory): Seq[Path] =
    findFiles(path => logFileNames.contains(path.getFileName.toString))

  /** Scripts that reproduce individual simulator invocations. */
  def replayScripts(using testingDirectory: HasTestingDirectory): Seq[Path] =
    findFiles(path => replayFileNames.contains(path.getFileName.toString))

  /** All waveform and diagnostic artifacts normally useful after a test failure. */
  def debugFiles(using testingDirectory: HasTestingDirectory): Seq[Path] =
    (traceFiles ++ logFiles ++ replayScripts).sortBy(_.toString)

  private def findFiles(select: Path => Boolean)(using testingDirectory: HasTestingDirectory): Seq[Path] =
    val root = testDirectory
    if !Files.isDirectory(root) then Seq.empty
    else
      val paths = Files.walk(root)
      try
        paths
          .iterator()
          .asScala
          .filter(path => Files.isRegularFile(path))
          .filter(select)
          .toVector
          .sortBy(_.toString)
      finally paths.close()
