package chipmunk.test

import java.nio.file.Files

import scala.jdk.CollectionConverters.*

import chisel3.*
import org.scalatest.ConfigMap

private object TraceSpecDut {
  final class Harness extends Module {
    val io = IO(new Bundle {
      val input  = Input(UInt(8.W))
      val output = Output(UInt(8.W))
    })

    val sampled = RegInit(0.U(8.W))
    sampled   := io.input
    io.output := sampled
  }
}

class TraceSpec extends ChipmunkFlatSpec {
  override def configMap: ConfigMap =
    super.configMap +
      ("simulator" -> "verilator") +
      ("emitFst"   -> "1")

  "EmitFst" should "write a non-empty FST" in {
    simulate(new TraceSpecDut.Harness, subdirectory = Some("fst-window")) { dut =>
      for value <- Seq(0x11, 0x22, 0x33, 0x44) do {
        dut.io.input #= value.U
        dut.clock.step()
        dut.io.output.expect(value.U)
      }
    }

    val root = implementation.getDirectory.resolve("fst-window")
    withClue(s"Simulation directory: $root") {
      Files.isDirectory(root) shouldBe true
    }

    val stream   = Files.walk(root)
    val fstFiles =
      try
        stream
          .iterator()
          .asScala
          .filter(path => Files.isRegularFile(path))
          .filter(path => path.getFileName.toString.endsWith(".fst"))
          .toVector
      finally stream.close()

    withClue(s"FST files under $root: ${fstFiles.mkString(", ")}") {
      fstFiles should not be empty
    }
    fstFiles.foreach { path =>
      withClue(s"FST file $path: ") {
        Files.size(path) should be > 1L
      }
    }
  }
}
