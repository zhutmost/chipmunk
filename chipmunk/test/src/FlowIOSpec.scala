package chipmunk.test

import scala.util.Random

import chisel3.*
import chisel3.util.Valid
import circt.stage.ChiselStage

import chipmunk.{Master, Slave}
import chipmunk.stream.*

private object FlowIOSpecDut {
  final class Payload extends Bundle {
    val tag  = UInt(3.W)
    val data = UInt(8.W)
  }

  final class CombinationalHarness extends Module {
    val io = IO(new Bundle {
      val in          = Slave(Flow(UInt(8.W)))
      val raw         = Flipped(Valid(new Payload))
      val condition   = Input(Bool())
      val replacement = Input(SInt(8.W))
      val mapped      = Master(Flow(Bool()))
      val inherited   = Master(Flow(UInt(9.W)))
      val replaced    = Master(Flow(SInt(8.W)))
      val signed      = Master(Flow(SInt(8.W)))
      val truncated   = Master(Flow(UInt(4.W)))
      val padded      = Master(Flow(UInt(12.W)))
      val nibbles     = Master(Flow(Vec(2, UInt(4.W))))
      val taken       = Master(Flow(UInt(8.W)))
      val thrown      = Master(Flow(UInt(8.W)))
      val stream      = Master(Stream(UInt(8.W)))
      val adapted     = Vec(3, Master(Flow(new Payload)))
      val handshake   = Master(Flow(UInt(8.W)))
      val emptyIn     = Vec(2, Slave(Flow()))
      val emptyOut    = Vec(2, Master(Flow.empty))
    })

    var evaluations = 0
    io.mapped << io.in.payloadMap { value =>
      evaluations += 1
      value >= 128.U
    }
    require(evaluations == 1, "payloadMap must evaluate its callback once")
    val inherited: FlowIO[UInt] = io.in.map(_ +& 1.U)
    io.inherited << inherited
    io.replaced << io.in.payloadReplace(io.replacement)
    io.signed << io.in.payloadCast(SInt(8.W), checkWidth = true)
    io.truncated << io.in.payloadCast(UInt(4.W))
    io.padded << io.in.payloadCast(UInt(12.W))
    io.nibbles << io.in.payloadCast(Vec(2, UInt(4.W)), checkWidth = true)
    io.taken << io.in.takeWhen(io.condition)
    io.thrown << io.in.throwWhen(io.condition)
    io.stream << io.in.asStream

    io.adapted(0) << Flow.from(io.raw)
    val similar = Wire(Flow.like(io.raw))
    similar connectFrom io.raw
    io.adapted(1) << similar
    io.adapted(2) connectFrom io.raw
    io.handshake handshakeFrom io.raw
    io.handshake.bits := 0xa5.U
    for (index <- 0 until 2) io.emptyOut(index) << io.emptyIn(index)
  }

  val paths = Seq(
    "pass-through"  -> 0,
    "forward"       -> 1,
    "stage zero"    -> 0,
    "stage default" -> 1,
    "stage three"   -> 3,
    "left chain"    -> 0,
    "right chain"   -> 0,
    "left pipe"     -> 2,
    "right pipe"    -> 2,
  )

  final class PipelineHarness extends Module {
    val io = IO(new Bundle {
      val in  = Slave(Flow(UInt(8.W)))
      val out = Vec(paths.size, Master(Flow(UInt(8.W))))
    })

    for (((name, _), index) <- paths.zipWithIndex) {
      val out    = io.out(index)
      val middle = Wire(Flow(UInt(8.W)))
      // Give the unused middle wire a driver for paths without a connection chain.
      middle.valid := false.B
      middle.bits  := 0.U
      name match {
        case "pass-through" =>
          require(io.in.pipePassThrough() eq io.in)
          out << io.in.pipePassThrough()
        case "forward"    => out << io.in.pipeForward()
        case "stage zero" =>
          require(io.in.stage(0) eq io.in)
          out << io.in.stage(0)
        case "stage default" => out << io.in.stage()
        case "stage three"   => out << io.in.stage(3)
        case "left chain"    => require((out << middle << io.in) eq io.in)
        case "right chain"   => require((io.in >> middle >> out) eq out)
        case "left pipe"     => require((out <-< middle <-< io.in) eq io.in)
        case "right pipe"    => require((io.in >-> middle >-> out) eq out)
        case _               => throw new IllegalArgumentException(name)
      }
    }
  }

  final class RegisterHarness extends Module {
    val io = IO(new Bundle {
      val writeValid = Input(Bool())
      val writeBits  = Input(Bool())
      val valid      = Input(Bool())
      val bits       = Input(UInt(8.W))
      val out        = Master(Flow(UInt(8.W)))
    })
    val stored = RegFlow(UInt(8.W))
    when(io.writeValid) { stored.valid := io.valid }
    when(io.writeBits) { stored.bits := io.bits }
    io.out << stored
  }

  final class InvalidHarness(mode: String) extends Module {
    val in = IO(Slave(Flow(UInt(8.W))))
    mode match {
      case "negative stages"      => in.stage(-1)
      case "unequal widths"       => in.payloadCast(UInt(7.W), checkWidth = true)
      case "unknown width"        => in.payloadCast(UInt(), checkWidth = true)
      case "unknown source width" =>
        val unknown = Wire(Flow(UInt()))
        unknown.payloadCast(UInt(8.W), checkWidth = true)
      case "unbound mapping"        => in.payloadMap(_ => UInt(8.W))
      case "unbound replacement"    => in.payloadReplace(UInt(8.W))
      case "bound cast type"        => in.payloadCast(in.bits)
      case "unbound factory source" => Flow.like(Valid(UInt(8.W)))
      case "write mapped"           => in.payloadMap(_ + 1.U).bits      := 0.U
      case "write inherited"        => in.map(_ + 1.U).valid            := false.B
      case "write replaced"         => in.payloadReplace(0.U(8.W)).bits := 0.U
      case "write cast"             => in.payloadCast(UInt(8.W)).bits   := 0.U
      case _                        => throw new IllegalArgumentException(mode)
    }
  }
}

class FlowIOSpec extends ChipmunkFlatSpec {
  import FlowIOSpecDut.*

  "FlowIO" should "transform, filter and adapt payloads without adding latency or backpressure" in {
    simulate(new CombinationalHarness) { dut =>
      for {
        value <- 0 until 256
        valid <- Seq(false, true)
      } {
        val condition   = (value & 1) == 0
        val replacement = value - 128
        dut.io.in.valid #= valid.B
        dut.io.in.bits #= value.U
        dut.io.condition #= condition.B
        dut.io.replacement #= replacement.S
        dut.io.stream.ready #= condition.B
        dut.io.raw.valid #= (!valid).B
        dut.io.raw.bits.tag #= (value & 7).U
        dut.io.raw.bits.data #= (255 - value).U
        for (index <- 0 until 2) dut.io.emptyIn(index).valid #= (valid ^ (index == 1)).B
        dut.clock.step(0)

        withClue(s"value=$value, valid=$valid: ") {
          Seq(
            dut.io.mapped.valid,
            dut.io.inherited.valid,
            dut.io.replaced.valid,
            dut.io.signed.valid,
            dut.io.truncated.valid,
            dut.io.padded.valid,
            dut.io.nibbles.valid,
            dut.io.stream.valid,
          ).foreach(_.expect(valid.B))
          if (valid) {
            dut.io.mapped.bits expect (value >= 128).B
            dut.io.inherited.bits expect (value + 1).U
            dut.io.replaced.bits expect replacement.S
            dut.io.signed.bits expect (if (value < 128) value else value - 256).S
            dut.io.truncated.bits expect (value & 15).U
            dut.io.padded.bits expect value.U
            dut.io.nibbles.bits(0) expect (value & 15).U
            dut.io.nibbles.bits(1) expect (value >>> 4).U
            dut.io.stream.bits expect value.U
          }
          dut.io.taken.valid expect (valid && condition).B
          dut.io.thrown.valid expect (valid && !condition).B
          if (valid && condition) dut.io.taken.bits expect value.U
          if (valid && !condition) dut.io.thrown.bits expect value.U
          for (out <- dut.io.adapted) {
            out.valid expect (!valid).B
            if (!valid) {
              out.bits.tag expect (value & 7).U
              out.bits.data expect (255 - value).U
            }
          }
          dut.io.handshake.valid expect (!valid).B
          dut.io.handshake.bits expect 0xa5.U
          for (index <- 0 until 2) dut.io.emptyOut(index).valid expect (valid ^ (index == 1)).B
        }
      }
    }
  }

  it should "preserve bubbles and full-rate traffic through pipelines and connection chains" in {
    simulate(new PipelineHarness) { dut =>
      dut.io.in.valid #= false.B
      dut.io.in.bits #= 0.U
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      val random  = new Random(0xf10L)
      var history = Vector.empty[Option[Int]]
      val held    = Array.fill[Option[Int]](paths.size)(None)

      for (cycle <- 0 until 160) {
        val valid = cycle < 24 || (cycle < 150 && random.nextBoolean())
        val value = random.nextInt(256)
        dut.io.in.valid #= valid.B
        dut.io.in.bits #= value.U
        dut.clock.step(0)
        for (((name, latency), index) <- paths.zipWithIndex) {
          val expected = if (latency == 0) Option.when(valid)(value) else history.lift(latency - 1).flatten
          withClue(s"$name, cycle=$cycle: ") {
            dut.io.out(index).valid expect expected.isDefined.B
            expected.foreach { bits =>
              dut.io.out(index).bits expect bits.U
              held(index) = Some(bits)
            }
            // Registered payloads hold the last valid value across bubbles; their reset value is unspecified.
            if (latency > 0 && expected.isEmpty) held(index).foreach(bits => dut.io.out(index).bits.expect(bits.U))
          }
        }
        dut.clock.step()
        history = (Option.when(valid)(value) +: history).take(3)
      }

      // Reset while every stage contains a transaction, then check that no old valid escapes.
      dut.io.in.valid #= true.B
      dut.io.in.bits #= 0x5a.U
      dut.clock.step(4)
      dut.io.in.valid #= false.B
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      for (_ <- 0 until 4) {
        dut.io.out.foreach(_.valid.expect(false.B))
        dut.clock.step()
      }
    }
  }

  it should "reset RegFlow valid and retain independently written fields" in {
    simulate(new RegisterHarness) { dut =>
      dut.io.writeValid #= false.B
      dut.io.writeBits #= false.B
      dut.io.valid #= false.B
      dut.io.bits #= 0.U
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      dut.io.out.valid expect false.B

      dut.io.writeValid #= true.B
      dut.io.writeBits #= true.B
      dut.io.valid #= true.B
      dut.io.bits #= 0x37.U
      dut.clock.step()
      dut.io.out.valid expect true.B
      dut.io.out.bits expect 0x37.U

      dut.io.writeValid #= false.B
      dut.io.bits #= 0xa9.U
      dut.clock.step()
      dut.io.out.valid expect true.B
      dut.io.out.bits expect 0xa9.U

      dut.io.writeBits #= false.B
      dut.io.bits #= 0xff.U
      dut.io.valid #= false.B
      dut.clock.step(3)
      dut.io.out.valid expect true.B
      dut.io.out.bits expect 0xa9.U

      dut.io.writeValid #= true.B
      dut.clock.step()
      dut.io.out.valid expect false.B
      dut.io.out.bits expect 0xa9.U
      dut.io.valid #= true.B
      dut.clock.step()
      dut.io.out.valid expect true.B

      dut.io.writeValid #= false.B
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      dut.io.out.valid expect false.B
      dut.io.out.bits expect 0xa9.U
    }
  }

  for (
    (mode, message) <- Seq(
      "negative stages"      -> "nonnegative",
      "unequal widths"       -> "Payload width mismatch",
      "unknown width"        -> "known source and target widths",
      "unknown source width" -> "known source and target widths",
    )
  ) {
    it should s"reject $mode during elaboration" in {
      val error = intercept[IllegalArgumentException] { ChiselStage.emitCHIRRTL(new InvalidHarness(mode)) }
      error.getMessage should include(message)
    }
  }

  for (mode <- Seq("unbound mapping", "unbound replacement", "unbound factory source")) {
    it should s"reject $mode during elaboration" in {
      intercept[ExpectedHardwareException] { ChiselStage.emitCHIRRTL(new InvalidHarness(mode)) }
    }
  }

  it should "require an unbound payload cast type" in {
    intercept[ExpectedChiselTypeException] { ChiselStage.emitCHIRRTL(new InvalidHarness("bound cast type")) }
  }

  for (mode <- Seq("write mapped", "write inherited", "write replaced", "write cast")) {
    it should s"reject attempts to $mode read-only flows" in {
      val error = intercept[ChiselException] {
        ChiselStage.emitCHIRRTL(new InvalidHarness(mode), args = Array("--throw-on-first-error"))
      }
      error.getMessage should include("Cannot connect to read-only value")
    }
  }
}
