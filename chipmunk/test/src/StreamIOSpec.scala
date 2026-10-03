package chipmunk.test

import scala.collection.mutable
import scala.util.Random

import chisel3.*
import chisel3.util.Decoupled
import circt.stage.ChiselStage

import chipmunk.{Master, Slave}
import chipmunk.stream.*

private object StreamIOSpecDut {
  final class Payload extends Bundle {
    val tag  = UInt(3.W)
    val data = UInt(8.W)
  }

  final class CombinationalHarness extends Module {
    val io = IO(new Bundle {
      val in          = Vec(7, Slave(Stream(UInt(8.W))))
      val replacement = Input(SInt(8.W))
      val mapped      = Master(Stream(Bool()))
      val inherited   = Master(Stream(UInt(9.W)))
      val replaced    = Master(Stream(SInt(8.W)))
      val signed      = Master(Stream(SInt(8.W)))
      val truncated   = Master(Stream(UInt(4.W)))
      val padded      = Master(Stream(UInt(12.W)))
      val nibbles     = Master(Stream(Vec(2, UInt(4.W))))
      val filterIn    = Vec(4, Slave(Stream(UInt(8.W))))
      val filterOut   = Vec(4, Master(Stream(UInt(8.W))))
      val condition   = Input(Bool())
      val statusIn    = Slave(Stream(UInt(8.W)))
      val statusReady = Input(Bool())
      val pending     = Output(Bool())
      val starving    = Output(Bool())
      val fire        = Output(Bool())
      val offered     = Master(Flow(UInt(8.W)))
      val transferred = Master(Flow(UInt(8.W)))
      val freeIn      = Slave(Stream(UInt(8.W)))
      val freeFlow    = Master(Flow(UInt(8.W)))
      val raw         = Vec(3, Flipped(Decoupled(new Payload)))
      val adapted     = Vec(3, Master(Stream(new Payload)))
      val handshakeIn = Flipped(Decoupled(new Payload))
      val handshake   = Master(Stream(UInt(8.W)))
      val emptyIn     = Vec(2, Slave(Stream()))
      val emptyOut    = Vec(2, Master(Stream.empty))
    })

    var evaluations = 0
    io.mapped << io.in(0).payloadMap { value =>
      evaluations += 1
      value >= 128.U
    }
    require(evaluations == 1, "payloadMap must evaluate its callback once")
    val inherited: StreamIO[UInt] = io.in(1).map(_ +& 1.U)
    io.inherited << inherited
    io.replaced << io.in(2).payloadReplace(io.replacement)
    io.signed << io.in(3).payloadCast(SInt(8.W), checkWidth = true)
    io.truncated << io.in(4).payloadCast(UInt(4.W))
    io.padded << io.in(5).payloadCast(UInt(12.W))
    io.nibbles << io.in(6).payloadCast(Vec(2, UInt(4.W)), checkWidth = true)
    io.filterOut(0) << io.filterIn(0).continueWhen(io.condition)
    io.filterOut(1) << io.filterIn(1).haltWhen(io.condition)
    io.filterOut(2) << io.filterIn(2).throwWhen(io.condition)
    io.filterOut(3) << io.filterIn(3).takeWhen(io.condition)
    io.statusIn.ready := io.statusReady
    io.pending        := io.statusIn.isPending
    io.starving       := io.statusIn.isStarving
    io.fire           := io.statusIn.fire
    io.offered << io.statusIn.asFlow
    io.transferred << io.statusIn.toFlow()
    io.freeFlow << io.freeIn.toFlow(readyFreeRun = true)
    io.adapted(0) << Stream.from(io.raw(0))
    io.adapted(1) << io.raw(1).toStream
    val similar = Wire(Stream.like(io.raw(2)))
    similar connectFrom io.raw(2)
    io.adapted(2) << similar
    io.handshake handshakeFrom io.handshakeIn
    io.handshake.bits := 0xa5.U
    for (index <- 0 until 2) io.emptyOut(index) << io.emptyIn(index)
  }

  // Latency and initiation interval with a continuously valid producer and an always-ready consumer.
  val paths = Seq(
    ("pass-through", 0, 1),
    ("forward", 1, 1),
    ("forward without collapse", 1, 1),
    ("backward", 0, 1),
    ("all", 1, 1),
    ("simple", 1, 2),
    ("valid", 1, 2),
    ("stage zero", 0, 1),
    ("stage default", 1, 1),
    ("stage three", 3, 1),
    ("queue zero", 0, 1),
    ("queue one", 1, 2),
    ("queue piped", 1, 1),
    ("queue flowing", 0, 1),
    ("queue piped flowing", 0, 1),
    ("queue four", 1, 1),
    ("queue synchronous", 1, 1),
    ("queue synchronous flowing", 0, 1),
    ("left chain", 0, 1),
    ("right chain", 0, 1),
    ("left forward", 2, 1),
    ("right forward", 2, 1),
    ("left backward", 0, 1),
    ("right backward", 0, 1),
    ("left all", 2, 1),
    ("right all", 2, 1),
  )

  final class PipelineHarness extends Module {
    val io = IO(new Bundle {
      val in  = Vec(paths.size, Slave(Stream(UInt(16.W))))
      val out = Vec(paths.size, Master(Stream(UInt(16.W))))
    })
    for (((name, _, _), index) <- paths.zipWithIndex) {
      val in  = io.in(index)
      val out = io.out(index)
      name match {
        case "pass-through" =>
          require(in.pipePassThrough() eq in)
          out << in.pipePassThrough()
        case "forward"                  => out << in.pipeForward()
        case "forward without collapse" => out << in.pipeForward(bubbleCollapse = false)
        case "backward"                 => out << in.pipeBackward()
        case "all"                      => out << in.pipeAll()
        case "simple"                   => out << in.pipeSimple()
        case "valid"                    => out << in.pipeValid()
        case "stage zero"               =>
          require(in.stage(0) eq in)
          out << in.stage(0)
        case "stage default" => out << in.stage()
        case "stage three"   => out << in.stage(3)
        case "queue zero"    =>
          require(in.queue(0) eq in)
          out << in.queue(0)
        case "queue one"                 => out << in.queue(1)
        case "queue piped"               => out << in.queue(1, queuePipe = true)
        case "queue flowing"             => out << in.queue(1, queueFlow = true)
        case "queue piped flowing"       => out << in.queue(1, queueFlow = true, queuePipe = true)
        case "queue four"                => out << in.queue(4)
        case "queue synchronous"         => out << in.queue(4, useSyncReadMem = true)
        case "queue synchronous flowing" => out << in.queue(4, queueFlow = true, useSyncReadMem = true)
        case _                           =>
          val middle = Wire(Stream(UInt(16.W)))
          name match {
            case "left chain"     => require((out << middle << in) eq in)
            case "right chain"    => require((in >> middle >> out) eq out)
            case "left forward"   => require((out <-< middle <-< in) eq in)
            case "right forward"  => require((in >-> middle >-> out) eq out)
            case "left backward"  => require((out <|< middle <|< in) eq in)
            case "right backward" => require((in >|> middle >|> out) eq out)
            case "left all"       => require((out <+< middle <+< in) eq in)
            case "right all"      => require((in >+> middle >+> out) eq out)
            case _                => throw new IllegalArgumentException(name)
          }
      }
    }
  }

  val queues = Seq(
    (4, false, false, false),
    (4, false, false, true),
    (4, true, false, true),
    (1, false, true, false),
    (1, true, true, false),
  )

  final class FlushHarness extends Module {
    val io = IO(new Bundle {
      val in    = Vec(queues.size, Slave(Stream(UInt(16.W))))
      val out   = Vec(queues.size, Master(Stream(UInt(16.W))))
      val flush = Input(Bool())
    })
    for (((depth, flow, pipe, sync), index) <- queues.zipWithIndex) {
      io.out(index) << io.in(index).queue(depth, flow, pipe, sync, flush = Some(io.flush))
    }
  }

  // Equal bounds exercise delayRandom's fixed-delay delegation, including zero.
  val delays = Seq(
    (false, 0, 0),
    (false, 1, 1),
    (false, 3, 3),
    (true, 0, 0),
    (true, 2, 2),
    (true, 0, 1),
    (true, 0, 3),
    (true, 2, 5),
  )

  final class DelayHarness extends Module {
    val io = IO(new Bundle {
      val in  = Vec(delays.size, Slave(Stream(UInt(16.W))))
      val out = Vec(delays.size, Master(Stream(UInt(16.W))))
    })
    for (((random, min, max), index) <- delays.zipWithIndex) {
      val in = io.in(index)
      if (min == 0 && max == 0) {
        require((if (random) in.delayRandom(max, min) else in.delayFixed(max)) eq in)
      }
      io.out(index) << (if (random) in.delayRandom(max, min) else in.delayFixed(max))
    }
  }

  final class InvalidHarness(mode: String) extends Module {
    val in = IO(Slave(Stream(UInt(8.W))))
    mode match {
      case "negative stages"         => in.stage(-1)
      case "negative queue"          => in.queue(-1)
      case "zero-depth flush"        => in.queue(0, flush = Some(false.B))
      case "negative fixed delay"    => in.delayFixed(-1)
      case "negative random minimum" => in.delayRandom(2, -1)
      case "reversed random bounds"  => in.delayRandom(1, 2)
      case "unequal widths"          => in.payloadCast(UInt(7.W), checkWidth = true)
      case "unknown width"           => in.payloadCast(UInt(), checkWidth = true)
      case "unknown source width"    =>
        val unknown = Wire(Stream(UInt()))
        unknown.payloadCast(UInt(8.W), checkWidth = true)
      case "unbound mapping"        => in.payloadMap(_ => UInt(8.W))
      case "unbound replacement"    => in.payloadReplace(UInt(8.W))
      case "bound cast type"        => in.payloadCast(in.bits)
      case "unbound factory source" => Stream.like(Decoupled(UInt(8.W)))
      case "write offered flow"     => in.asFlow.bits    := 0.U
      case "write transferred flow" => in.toFlow().valid := false.B
      case _                        => throw new IllegalArgumentException(mode)
    }
  }
}

class StreamIOSpec extends ChipmunkFlatSpec {
  import StreamIOSpecDut.*

  "StreamIO" should "preserve handshake timing through payload transformations and ready/valid adapters" in {
    simulate(new CombinationalHarness) { dut =>
      val outputs = Seq(
        dut.io.mapped,
        dut.io.inherited,
        dut.io.replaced,
        dut.io.signed,
        dut.io.truncated,
        dut.io.padded,
        dut.io.nibbles,
      )
      for {
        value     <- Seq(0x00, 0x01, 0x7f, 0x80, 0xa5, 0xff)
        valid     <- Seq(false, true)
        ready     <- Seq(false, true)
        condition <- Seq(false, true)
      } {
        dut.io.replacement #= (value - 128).S
        dut.io.condition #= condition.B
        for (in <- dut.io.in) {
          in.valid #= valid.B
          in.bits #= value.U
        }
        outputs.foreach(_.ready #= ready.B)
        for (in <- dut.io.filterIn) {
          in.valid #= valid.B
          in.bits #= value.U
        }
        dut.io.filterOut.foreach(_.ready #= ready.B)
        dut.io.statusIn.valid #= valid.B
        dut.io.statusIn.bits #= value.U
        dut.io.statusReady #= ready.B
        dut.io.freeIn.valid #= valid.B
        dut.io.freeIn.bits #= value.U
        for (in <- dut.io.raw) {
          in.valid #= valid.B
          in.bits.tag #= (value & 7).U
          in.bits.data #= value.U
        }
        dut.io.adapted.foreach(_.ready #= ready.B)
        dut.io.handshakeIn.valid #= valid.B
        dut.io.handshakeIn.bits.tag #= 0.U
        dut.io.handshakeIn.bits.data #= 0.U
        dut.io.handshake.ready #= ready.B
        for (index <- 0 until 2) {
          dut.io.emptyIn(index).valid #= (valid ^ (index == 1)).B
          dut.io.emptyOut(index).ready #= (ready ^ (index == 1)).B
        }
        dut.clock.step(0)

        withClue(s"value=$value, valid=$valid, ready=$ready, condition=$condition: ") {
          outputs.foreach(_.valid.expect(valid.B))
          dut.io.in.foreach(_.ready.expect(ready.B))
          if (valid) {
            dut.io.mapped.bits expect (value >= 128).B
            dut.io.inherited.bits expect (value + 1).U
            dut.io.replaced.bits expect (value - 128).S
            dut.io.signed.bits expect (if (value < 128) value else value - 256).S
            dut.io.truncated.bits expect (value & 15).U
            dut.io.padded.bits expect value.U
            dut.io.nibbles.bits(0) expect (value & 15).U
            dut.io.nibbles.bits(1) expect (value >>> 4).U
          }
          val allowed = Seq(condition, !condition, !condition, condition)
          for (index <- allowed.indices) {
            dut.io.filterOut(index).valid expect (valid && allowed(index)).B
            val inputReady = if (index < 2) ready && allowed(index) else ready || !allowed(index)
            dut.io.filterIn(index).ready expect inputReady.B
            if (valid && allowed(index)) dut.io.filterOut(index).bits expect value.U
          }
          dut.io.statusIn.ready expect ready.B
          dut.io.pending expect (valid && !ready).B
          dut.io.starving expect (!valid && ready).B
          dut.io.fire expect (valid && ready).B
          dut.io.offered.valid expect valid.B
          dut.io.transferred.valid expect (valid && ready).B
          dut.io.freeIn.ready expect true.B
          dut.io.freeFlow.valid expect valid.B
          if (valid) {
            dut.io.offered.bits expect value.U
            dut.io.freeFlow.bits expect value.U
            if (ready) dut.io.transferred.bits expect value.U
          }
          for (index <- 0 until 3) {
            dut.io.raw(index).ready expect ready.B
            dut.io.adapted(index).valid expect valid.B
            if (valid) {
              dut.io.adapted(index).bits.tag expect (value & 7).U
              dut.io.adapted(index).bits.data expect value.U
            }
          }
          dut.io.handshakeIn.ready expect ready.B
          dut.io.handshake.valid expect valid.B
          dut.io.handshake.bits expect 0xa5.U
          for (index <- 0 until 2) {
            dut.io.emptyOut(index).valid expect (valid ^ (index == 1)).B
            dut.io.emptyIn(index).ready expect (ready ^ (index == 1)).B
          }
        }
      }
    }
  }

  private def resetPipelines(dut: PipelineHarness): Unit = {
    for (index <- paths.indices) {
      dut.io.in(index).valid #= false.B
      dut.io.in(index).bits #= 0.U
      dut.io.out(index).ready #= true.B
    }
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
  }

  it should "meet documented latency and throughput and discard buffered transactions on reset" in {
    simulate(new PipelineHarness) { dut =>
      resetPipelines(dut)
      val accepted = Array.fill(paths.size)(0)
      for (cycle <- 0 until 40) {
        for (index <- paths.indices) {
          dut.io.in(index).valid #= true.B
          dut.io.in(index).bits #= accepted(index).U
        }
        dut.clock.step(0)
        for (((name, latency, interval), index) <- paths.zipWithIndex) {
          val valid = cycle >= latency && (cycle - latency) % interval == 0
          withClue(s"$name, cycle=$cycle: ") {
            dut.io.out(index).valid expect valid.B
            if (valid) dut.io.out(index).bits expect ((cycle - latency) / interval).U
          }
          if (dut.io.in(index).ready.peek().litToBoolean) accepted(index) += 1
        }
        dut.clock.step()
      }

      // Fill every buffering path and leave valid-only delays pending before asserting reset.
      dut.io.out.foreach(_.ready #= false.B)
      for (index <- paths.indices) dut.io.in(index).bits #= accepted(index).U
      dut.clock.step(6)
      resetPipelines(dut)
      for (_ <- 0 until 6) {
        dut.io.out.foreach(_.valid.expect(false.B))
        dut.clock.step()
      }
    }
  }

  it should "honor the distinct buffering and refill rules of each pipeline" in {
    simulate(new PipelineHarness) { dut =>
      def select(name: String): (StreamIO[UInt], StreamIO[UInt]) = {
        resetPipelines(dut)
        val index = paths.indexWhere(_._1 == name)
        (dut.io.in(index), dut.io.out(index))
      }

      for (collapse <- Seq(true, false)) {
        val (in, out) = select(if (collapse) "forward" else "forward without collapse")
        in.valid #= true.B
        in.bits #= 0x31.U
        out.ready #= false.B
        in.ready expect collapse.B
        out.valid expect false.B
        dut.clock.step()
        out.valid expect collapse.B
        if (!collapse) {
          out.ready #= true.B
          dut.clock.step()
          out.ready #= false.B
        }
        in.bits #= 0x32.U // The first transaction has now been accepted.
        in.ready expect false.B
        out.valid expect true.B
        out.bits expect 0x31.U
        dut.clock.step(3)
        out.bits expect 0x31.U
        out.ready #= true.B
        in.ready expect true.B
        dut.clock.step()
        out.valid expect true.B
        out.bits expect 0x32.U
      }

      val (backIn, backOut) = select("backward")
      backIn.valid #= true.B
      backIn.bits #= 0x41.U
      backOut.ready #= false.B
      backIn.ready expect true.B
      backOut.valid expect true.B
      backOut.bits expect 0x41.U
      dut.clock.step()
      backIn.bits #= 0x42.U
      backIn.ready expect false.B
      backOut.bits expect 0x41.U
      dut.clock.step(3)
      backOut.ready #= true.B
      backIn.ready expect false.B // Draining the skid buffer cannot also accept input.
      dut.clock.step()
      backIn.ready expect true.B
      backOut.valid expect true.B
      backOut.bits expect 0x42.U

      val (allIn, allOut) = select("all")
      allOut.ready #= false.B
      allIn.valid #= true.B
      allIn.bits #= 0x51.U
      allIn.ready expect true.B
      dut.clock.step()
      allIn.bits #= 0x52.U
      allIn.ready expect true.B
      dut.clock.step()
      allIn.bits #= 0x53.U
      allIn.ready expect false.B
      allOut.bits expect 0x51.U
      allOut.ready #= true.B
      allIn.ready expect false.B
      dut.clock.step()
      allIn.ready expect true.B
      allOut.bits expect 0x52.U
      dut.clock.step()
      allOut.bits expect 0x53.U

      val (simpleIn, simpleOut) = select("simple")
      simpleIn.valid #= true.B
      simpleIn.bits #= 0x61.U
      simpleOut.ready #= false.B
      simpleIn.ready expect true.B
      dut.clock.step()
      simpleIn.bits #= 0x62.U
      simpleIn.ready expect false.B
      simpleOut.valid expect true.B
      simpleOut.bits expect 0x61.U
      simpleOut.ready #= true.B
      simpleIn.ready expect false.B
      dut.clock.step()
      simpleOut.valid expect false.B
      simpleIn.ready expect true.B

      val (validIn, validOut) = select("valid")
      validIn.valid #= true.B
      validIn.bits #= 0x71.U
      validOut.ready #= false.B
      validIn.ready expect false.B
      validOut.valid expect false.B
      dut.clock.step()
      validIn.ready expect false.B
      validOut.valid expect true.B
      validOut.bits expect 0x71.U
      dut.clock.step(3)
      validOut.ready #= true.B
      validIn.ready expect true.B // Payload is held at the producer until this simultaneous transfer.
      dut.clock.step()
      validIn.bits #= 0x72.U
      validIn.ready expect false.B
      validOut.valid expect false.B
    }
  }

  it should "preserve every accepted transaction and stalled offer under bubbles and backpressure" in {
    simulate(new PipelineHarness) { dut =>
      resetPipelines(dut)
      val random   = new Random(0x57eaL)
      val expected = Array.fill(paths.size)(mutable.Queue.empty[Int])
      val offered  = Array.fill[Option[Int]](paths.size)(None)
      val stalled  = Array.fill[Option[Int]](paths.size)(None)
      val sent     = Array.fill(paths.size)(0)
      val received = Array.fill(paths.size)(0)

      for (cycle <- 0 until 420) {
        val draining = cycle >= 340
        for (index <- paths.indices) {
          if (!draining && offered(index).isEmpty && (cycle < 30 || random.nextInt(4) != 0)) {
            offered(index) = Some(sent(index) + 1)
          }
          dut.io.in(index).valid #= offered(index).isDefined.B
          dut.io.in(index).bits #= offered(index).getOrElse(0).U
          val ready = draining || (cycle >= 30 && (cycle >= 50 || index % 2 == 0) && random.nextBoolean())
          dut.io.out(index).ready #= ready.B
        }
        dut.clock.step(0)

        for (((name, _, _), index) <- paths.zipWithIndex) {
          val inReady  = dut.io.in(index).ready.peek().litToBoolean
          val outValid = dut.io.out(index).valid.peek().litToBoolean
          val outReady = dut.io.out(index).ready.peek().litToBoolean
          withClue(s"$name, cycle=$cycle: ") {
            // Enqueue before comparing output so zero-latency paths can transfer in this cycle.
            if (offered(index).isDefined && inReady) {
              expected(index).enqueue(offered(index).get)
              sent(index) += 1
              offered(index) = None
            }
            stalled(index).foreach { bits =>
              dut.io.out(index).valid expect true.B
              dut.io.out(index).bits expect bits.U
            }
            if (outValid && outReady) {
              expected(index).nonEmpty shouldBe true
              dut.io.out(index).bits expect expected(index).dequeue().U
              received(index) += 1
            }
            stalled(index) = Option.when(outValid && !outReady)(dut.io.out(index).bits.peek().litValue.toInt)
          }
        }
        dut.clock.step()
      }
      for (((name, _, _), index) <- paths.zipWithIndex) {
        withClue(s"$name after draining: ") {
          expected(index) shouldBe empty
          offered(index) shouldBe None
          received(index) shouldBe sent(index)
          received(index) should be > 40
          dut.io.out(index).valid expect false.B
        }
      }
    }
  }

  it should "flush full queues, including synchronous memories and simultaneous input/output transfers" in {
    simulate(new FlushHarness) { dut =>
      dut.io.flush #= false.B
      for (index <- queues.indices) {
        dut.io.in(index).valid #= false.B
        dut.io.in(index).bits #= 0.U
        dut.io.out(index).ready #= false.B
      }
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      for (cycle <- 0 until 4) {
        for (((depth, _, _, _), index) <- queues.zipWithIndex) {
          dut.io.in(index).valid #= (cycle < depth).B
          dut.io.in(index).bits #= (0x100 + cycle).U
          dut.io.in(index).ready expect (cycle < depth).B
        }
        dut.clock.step()
      }
      dut.io.in.foreach(_.valid #= false.B)
      for (index <- queues.indices) {
        dut.io.in(index).ready expect false.B
        dut.io.out(index).valid expect true.B
        dut.io.out(index).bits expect 0x100.U
      }
      dut.io.flush #= true.B
      dut.clock.step()
      dut.io.flush #= false.B
      dut.io.out.foreach(_.valid.expect(false.B))
      dut.io.in.foreach(_.ready.expect(true.B))

      dut.io.in.foreach { in =>
        in.valid #= true.B
        in.bits #= 0x200.U
      }
      dut.clock.step()
      dut.io.in.foreach(_.valid #= false.B)
      dut.io.out.foreach(_.bits.expect(0x200.U))
      dut.io.out.foreach(_.valid.expect(true.B))
      dut.io.out.foreach(_.ready #= true.B)
      dut.clock.step()
      dut.io.out.foreach(_.valid.expect(false.B))

      // Enqueue a transaction, then flush on the edge that transfers it and offers its successor.
      dut.io.out.foreach(_.ready #= false.B)
      dut.io.in.foreach { in =>
        in.valid #= true.B
        in.bits #= 0x300.U
      }
      dut.clock.step()
      dut.io.in.foreach(_.bits #= 0x301.U)
      dut.io.out.foreach(_.ready #= true.B)
      dut.io.flush #= true.B
      for (index <- queues.indices) {
        dut.io.in(index).ready expect true.B
        dut.io.out(index).valid expect true.B
        dut.io.out(index).bits expect 0x300.U
      }
      dut.clock.step()
      dut.io.in.foreach(_.valid #= false.B)
      dut.io.flush #= false.B
      for (_ <- 0 until 5) {
        dut.io.out.foreach(_.valid.expect(false.B))
        dut.clock.step()
      }
    }
  }

  private def resetDelays(dut: DelayHarness): Unit = {
    for (index <- delays.indices) {
      dut.io.in(index).valid #= false.B
      dut.io.in(index).bits #= 0.U
      dut.io.out(index).ready #= true.B
    }
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
    dut.clock.step(3) // Idle cycles must not start the per-transaction timer.
    dut.io.out.foreach(_.valid.expect(false.B))
  }

  private def delayRound(dut: DelayHarness, stall: Boolean): Vector[Vector[Int]] = {
    val observed = Array.fill(delays.size)(Vector.empty[Int])
    for (transaction <- 0 until 32) {
      val done  = Array.fill(delays.size)(false)
      val first = Array.fill[Option[Int]](delays.size)(None)
      var cycle = 0
      while (!done.forall(identity) && cycle < 12) {
        val ready = !stall || cycle >= 8
        for (index <- delays.indices) {
          dut.io.in(index).valid #= (!done(index)).B
          dut.io.in(index).bits #= (transaction + 1).U
          dut.io.out(index).ready #= ready.B
        }
        dut.clock.step(0)
        for (((_, min, max), index) <- delays.zipWithIndex if !done(index)) {
          val valid = dut.io.out(index).valid.peek().litToBoolean
          withClue(s"delay=${delays(index)}, transaction=$transaction, cycle=$cycle, stall=$stall: ") {
            dut.io.in(index).ready expect (valid && ready).B
            if (min == max) dut.io.out(index).valid expect (cycle >= min).B
            if (valid) {
              dut.io.out(index).bits expect (transaction + 1).U
              if (first(index).isEmpty) {
                cycle should be >= min
                cycle should be <= max
                first(index) = Some(cycle)
                observed(index) :+= cycle
              }
            }
            if (first(index).isDefined) dut.io.out(index).valid expect true.B
            if (valid && ready) done(index) = true
          }
        }
        dut.clock.step()
        cycle += 1
      }
      withClue(s"transaction=$transaction, stall=$stall: ") { done.forall(identity) shouldBe true }
    }
    dut.io.in.foreach(_.valid #= false.B)
    dut.clock.step()
    dut.io.out.foreach(_.valid.expect(false.B))
    observed.toVector
  }

  it should "delay presentation within inclusive bounds, hold under backpressure and replay after reset" in {
    simulate(new DelayHarness) { dut =>
      resetDelays(dut)
      val first = delayRound(dut, stall = false)
      for (((random, min, max), index) <- delays.zipWithIndex if random && min != max) {
        withClue(s"delay=${delays(index)}: ") {
          first(index) should contain(min)
          first(index) should contain(max)
        }
      }
      resetDelays(dut)
      delayRound(dut, stall = false) shouldBe first
      delayRound(dut, stall = true)

      // A still-offered transaction restarts its fixed delay when reset interrupts the countdown.
      val index = delays.indexOf((false, 3, 3))
      dut.io.in(index).valid #= true.B
      dut.io.in(index).bits #= 0x456.U
      dut.io.out(index).ready #= false.B
      dut.clock.step(2)
      dut.io.out(index).valid expect false.B
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      for (_ <- 0 until 3) {
        dut.io.out(index).valid expect false.B
        dut.io.in(index).ready expect false.B
        dut.clock.step()
      }
      dut.io.out(index).valid expect true.B
      dut.io.out(index).bits expect 0x456.U
      dut.io.out(index).ready #= true.B
      dut.io.in(index).ready expect true.B
      dut.clock.step()
      dut.io.in(index).valid #= false.B
      dut.io.out(index).valid expect false.B
    }
  }

  for (
    (mode, message) <- Seq(
      "negative stages"         -> "nonnegative",
      "negative queue"          -> "nonnegative",
      "zero-depth flush"        -> "cannot be flushed",
      "negative fixed delay"    -> "nonnegative",
      "negative random minimum" -> "nonnegative",
      "reversed random bounds"  -> "at least the minimum",
      "unequal widths"          -> "Payload width mismatch",
      "unknown width"           -> "known source and target widths",
      "unknown source width"    -> "known source and target widths",
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

  for (mode <- Seq("write offered flow", "write transferred flow")) {
    it should s"reject attempts to $mode through a read-only view" in {
      val error = intercept[ChiselException] {
        ChiselStage.emitCHIRRTL(new InvalidHarness(mode), args = Array("--throw-on-first-error"))
      }
      error.getMessage should include("Cannot connect to read-only value")
    }
  }
}
