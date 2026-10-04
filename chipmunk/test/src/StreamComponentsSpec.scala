package chipmunk.test

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

import chisel3.*
import chisel3.util.{MixedVec, MixedVecInit}
import circt.stage.ChiselStage

import chipmunk.{Master, Slave}
import chipmunk.stream.*

private object StreamComponentsSpecDut {
  final class ArbiterHarness(num: Int, roundRobin: Boolean) extends Module {
    val io = IO(new Bundle {
      val ins = Vec(num, Slave(Stream(UInt(8.W))))
      val out = Master(Stream(UInt(8.W)))
    })
    io.out << (if (roundRobin) StreamArbiter.roundRobin(io.ins) else StreamArbiter.lowerFirst(io.ins))
  }

  final class RoutingHarness(num: Int, tokens: Boolean) extends Module {
    val io = IO(new Bundle {
      val muxIns      = Vec(num, Slave(Stream(UInt(8.W))))
      val muxSelect   = Slave(Stream(UInt(4.W)))
      val muxOut      = Master(Stream(UInt(8.W)))
      val demuxIn     = Slave(Stream(UInt(8.W)))
      val demuxSelect = Slave(Stream(UInt(4.W)))
      val demuxOuts   = Vec(num, Master(Stream(UInt(8.W))))
    })
    if (tokens) {
      io.muxOut << StreamMux(io.muxSelect, io.muxIns)
      io.demuxOuts.zip(StreamDemux(io.demuxIn, io.demuxSelect, num)).foreach { case (sink, source) => sink << source }
    } else {
      io.muxSelect.ready   := false.B
      io.demuxSelect.ready := false.B
      io.muxOut << StreamMux(io.muxSelect.bits, io.muxIns)
      io.demuxOuts.zip(StreamDemux(io.demuxIn, io.demuxSelect.bits, num)).foreach { case (sink, source) =>
        sink << source
      }
    }
  }

  final class ForkHarness(num: Int) extends Module {
    val io = IO(new Bundle {
      val in   = Slave(Stream(UInt(8.W)))
      val outs = Vec(num, Master(Stream(UInt(8.W))))
    })
    io.outs.zip(StreamFork.duplicate(io.in, num)).foreach { case (sink, source) => sink << source }
  }

  final class JoinHarness(num: Int) extends Module {
    val io = IO(new Bundle {
      val ins = Vec(num, Slave(Stream(UInt(8.W))))
      val out = Master(Stream(Vec(num, UInt(8.W))))
    })
    io.out << StreamJoin.vecMerge(io.ins)
  }

  final class JoinedPayload extends Bundle {
    val addr = UInt(4.W)
    val data = SInt(8.W)
  }

  final class PayloadHarness extends Module {
    val io = IO(new Bundle {
      val addr       = Slave(Stream(UInt(4.W)))
      val data       = Slave(Stream(SInt(8.W)))
      val joined     = Master(Stream(new JoinedPayload))
      val splitIn    = Slave(Stream(Vec(2, UInt(8.W))))
      val splitOuts  = Vec(2, Master(Stream(UInt(8.W))))
      val mixedIn    = Slave(Stream(MixedVec(Seq(UInt(4.W), UInt(9.W)))))
      val mixedLow   = Master(Stream(UInt(4.W)))
      val mixedHigh  = Master(Stream(UInt(9.W)))
      val mixedA     = Slave(Stream(UInt(4.W)))
      val mixedB     = Slave(Stream(Bool()))
      val mixedJoin  = Master(Stream(MixedVec(Seq(UInt(4.W), Bool()))))
      val flow       = Slave(Flow(UInt(8.W)))
      val copies     = Vec(2, Master(Flow(UInt(8.W))))
      val flowLow    = Master(Flow(UInt(4.W)))
      val flowHigh   = Master(Flow(UInt(9.W)))
      val stream     = Slave(Stream(UInt(8.W)))
      val priorityIn = Slave(Flow(UInt(8.W)))
      val priority   = Master(Flow(UInt(8.W)))
    })
    var evaluations = 0
    io.joined << StreamJoin.map(io.addr, io.data) { (addr, data) =>
      evaluations += 1
      val result = Wire(new JoinedPayload)
      result.addr := addr
      result.data := data
      result
    }
    require(evaluations == 1)
    io.splitOuts.zip(StreamFork.vecSplit(io.splitIn)).foreach { case (sink, source) => sink << source }
    val mixedOuts = StreamFork.vecMap(io.mixedIn, bits => MixedVecInit(bits(0), bits(1)))
    io.mixedLow << mixedOuts(0)
    io.mixedHigh << mixedOuts(1)
    io.mixedJoin << StreamJoin.mixedVecMerge(io.mixedA, io.mixedB)
    io.copies.zip(FlowFork.duplicate(io.flow, 2)).foreach { case (sink, source) => sink << source }
    val mappedFlow = FlowFork.vecMap(io.flow, bits => MixedVecInit(bits(3, 0), bits +& 1.U))
    io.flowLow << mappedFlow(0)
    io.flowHigh << mappedFlow(1)
    io.priority << StreamFlowArbiter(io.stream, io.priorityIn)
  }

  final class QueueHarness extends Module {
    val io = IO(new Bundle {
      val in      = Slave(Stream(UInt(8.W)))
      val out     = Master(Stream(UInt(8.W)))
      val flush   = Input(Bool())
      val zeroIn  = Slave(Stream(UInt(8.W)))
      val zeroOut = Master(Stream(UInt(8.W)))
    })
    io.out << StreamQueue(io.in, entries = 2, pipe = true, flush = Some(io.flush))
    io.zeroOut << StreamQueue(io.zeroIn, entries = 0)
  }

  final class InvalidHarness(mode: String) extends Module {
    private val stream = Wire(Stream(UInt(8.W)))
    private val flow   = Wire(Flow(UInt(8.W)))
    stream.valid := false.B
    stream.bits  := 0.U
    flow.valid   := false.B
    flow.bits    := 0.U
    mode match {
      case "arbiter"      => StreamArbiter.roundRobin(Seq.empty[StreamIO[UInt]])
      case "mux"          => StreamMux(0.U, Seq.empty[StreamIO[UInt]])
      case "demux"        => StreamDemux(stream, 0.U, 0)
      case "fork"         => StreamFork.duplicate(stream, 0)
      case "flow fork"    => FlowFork.duplicate(flow, 0)
      case "join"         => StreamJoin.withoutPayload(Seq.empty)
      case "queue"        => StreamQueue(stream, -1)
      case "zero flush"   => StreamQueue(stream, 0, flush = Some(false.B))
      case "mux module"   => Module(new StreamMux(UInt(8.W), 0))
      case "demux module" => Module(new StreamDemux(UInt(8.W), 0))
      case other          => throw new IllegalArgumentException(other)
    }
  }
}

class StreamComponentsSpec extends ChipmunkFlatSpec {
  import StreamComponentsSpecDut.*

  private def reset(dut: Module): Unit = {
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
  }

  for (roundRobin <- Seq(false, true)) {
    "StreamArbiter" should s"hold choices and transfer exactly one source under random stalls (roundRobin=$roundRobin)" in {
      simulate(new ArbiterHarness(3, roundRobin)) { dut =>
        dut.io.ins.foreach { in => in.valid #= false.B; in.bits #= 0.U }
        dut.io.out.ready #= false.B
        reset(dut)

        // Offer input two, stall it, then introduce higher-priority requests.
        dut.io.ins(2).valid #= true.B
        dut.io.ins(2).bits #= 0x80.U
        dut.io.out.bits expect 0x80.U
        dut.clock.step()
        for (i <- 0 until 2) {
          dut.io.ins(i).valid #= true.B
          dut.io.ins(i).bits #= (i * 64).U
        }
        dut.clock.step(3)
        dut.io.out.valid expect true.B
        dut.io.out.bits expect 0x80.U
        dut.io.ins.foreach(_.ready.expect(false.B))

        val random   = new Random(0xa7b1L)
        val accepted = Array.fill(3)(0)
        val offered  = Array.fill(3)(true)
        var held     = Option(2)
        var last     = 0
        var cycles   = 0
        while (accepted.exists(_ < 24) && cycles < 500) {
          for (i <- 0 until 3) {
            if (!offered(i) && accepted(i) < 24 && random.nextBoolean()) offered(i) = true
            dut.io.ins(i).valid #= offered(i).B
            dut.io.ins(i).bits #= (i * 64 + accepted(i)).U
          }
          val ready = cycles > 400 || random.nextBoolean()
          dut.io.out.ready #= ready.B
          val order  = if (roundRobin) (1 to 3).map(offset => (last + offset) % 3) else 0 until 3
          val choice = held.orElse(order.find(offered(_)))
          dut.io.out.valid expect choice.nonEmpty.B
          choice.foreach(i => dut.io.out.bits.expect((i * 64 + accepted(i)).U))
          // Ready on an invalid source is unconstrained; check which offered transaction can transfer.
          for (i <- 0 until 3 if offered(i)) dut.io.ins(i).ready.expect((choice.contains(i) && ready).B)
          if (choice.nonEmpty && ready) {
            val i = choice.get
            accepted(i) += 1
            offered(i) = false
            last = i
            held = None
          } else if (choice.nonEmpty) held = choice
          dut.clock.step()
          cycles += 1
        }
        accepted.toSeq shouldBe Seq(24, 24, 24)
      }
    }
  }

  "StreamMux and StreamDemux" should "hold external selections while stalled and reject full-width invalid indices" in {
    simulate(new RoutingHarness(3, tokens = false)) { dut =>
      dut.io.muxIns.zipWithIndex.foreach { case (in, i) => in.valid #= true.B; in.bits #= (0x30 + i).U }
      dut.io.muxSelect.valid #= false.B
      dut.io.muxSelect.bits #= 2.U
      dut.io.muxOut.ready #= false.B
      dut.io.demuxIn.valid #= true.B
      dut.io.demuxIn.bits #= 0x55.U
      dut.io.demuxSelect.valid #= false.B
      dut.io.demuxSelect.bits #= 1.U
      dut.io.demuxOuts.foreach(_.ready #= false.B)
      reset(dut)
      dut.clock.step()
      dut.io.muxSelect.bits #= 0.U
      dut.io.demuxSelect.bits #= 2.U
      dut.clock.step(3)
      dut.io.muxOut.bits expect 0x32.U
      dut.io.demuxOuts(1).valid expect true.B
      dut.io.demuxOuts(2).valid expect false.B
      dut.io.muxOut.ready #= true.B
      dut.io.demuxOuts(1).ready #= true.B
      dut.io.muxIns(2).ready expect true.B
      dut.io.muxIns(0).ready expect false.B
      dut.io.demuxIn.ready expect true.B
      dut.clock.step()
      dut.io.muxOut.bits expect 0x30.U
      dut.io.demuxOuts(2).valid expect true.B
      dut.io.demuxOuts(2).ready #= true.B
      dut.clock.step()
      for (invalid <- Seq(3, 4, 8, 15)) {
        dut.io.muxSelect.bits #= invalid.U
        dut.io.demuxSelect.bits #= invalid.U
        dut.io.muxOut.valid expect false.B
        dut.io.muxIns.foreach(_.ready.expect(false.B))
        dut.io.demuxIn.ready expect false.B
        dut.io.demuxOuts.foreach(_.valid.expect(false.B))
        dut.clock.step()
      }
    }
  }

  it should "consume selection tokens only with their data transactions" in {
    simulate(new RoutingHarness(3, tokens = true)) { dut =>
      dut.io.muxIns.zipWithIndex.foreach { case (in, i) => in.valid #= true.B; in.bits #= (0x40 + i).U }
      dut.io.muxSelect.valid #= false.B
      dut.io.muxSelect.bits #= 2.U
      dut.io.muxOut.ready #= true.B
      dut.io.demuxIn.valid #= false.B
      dut.io.demuxIn.bits #= 0x66.U
      dut.io.demuxSelect.valid #= true.B
      dut.io.demuxSelect.bits #= 1.U
      dut.io.demuxOuts.foreach(_.ready #= true.B)
      reset(dut)
      dut.io.muxOut.valid expect false.B
      dut.io.muxIns.foreach(_.ready.expect(false.B))
      dut.io.demuxSelect.ready expect false.B
      dut.io.muxSelect.valid #= true.B
      dut.io.muxOut.ready #= false.B
      dut.io.demuxIn.valid #= true.B
      dut.io.demuxOuts(1).ready #= false.B
      dut.clock.step(3)
      dut.io.muxOut.valid expect true.B
      dut.io.muxOut.bits expect 0x42.U
      dut.io.muxSelect.ready expect false.B
      dut.io.demuxSelect.ready expect false.B
      dut.io.demuxIn.ready expect false.B
      dut.io.muxOut.ready #= true.B
      dut.io.demuxOuts(1).ready #= true.B
      dut.io.muxSelect.ready expect true.B
      dut.io.muxIns(2).ready expect true.B
      dut.io.demuxSelect.ready expect true.B
      dut.io.demuxIn.ready expect true.B
      dut.clock.step()
      dut.io.muxSelect.bits #= 8.U
      dut.io.demuxSelect.bits #= 8.U
      dut.io.muxOut.valid expect false.B
      dut.io.muxSelect.ready expect false.B
      dut.io.demuxSelect.ready expect false.B
      dut.io.demuxOuts.foreach(_.valid.expect(false.B))
    }
  }

  "StreamFork" should "broadcast exactly once per branch under independent randomized backpressure" in {
    simulate(new ForkHarness(3)) { dut =>
      dut.io.in.valid #= false.B
      dut.io.in.bits #= 0.U
      dut.io.outs.foreach(_.ready #= false.B)
      reset(dut)
      val random = new Random(0xf07aL)
      val seen   = Array.fill(3)(ArrayBuffer.empty[Int])
      var next   = 0
      var cycles = 0
      while ((next < 40 || seen.exists(_.length < 40)) && cycles < 600) {
        dut.io.in.valid #= (next < 40).B
        dut.io.in.bits #= next.U
        dut.io.outs.foreach(_.ready #= (cycles > 500 || random.nextBoolean()).B)
        for (i <- 0 until 3) {
          if (dut.io.outs(i).valid.peek().litToBoolean && dut.io.outs(i).ready.peek().litToBoolean) {
            val value = dut.io.outs(i).bits.peek().litValue.toInt
            value shouldBe seen(i).length
            seen(i) += value
          }
        }
        if (next < 40 && dut.io.in.ready.peek().litToBoolean) next += 1
        dut.clock.step()
        cycles += 1
      }
      next shouldBe 40
      seen.foreach(_.toSeq shouldBe (0 until 40))
      dut.io.in.valid #= true.B
      dut.io.in.bits #= 0xaa.U
      dut.io.outs.zipWithIndex.foreach { case (out, i) => out.ready #= (i == 0).B }
      dut.clock.step()
      dut.io.outs(0).valid expect false.B
      dut.io.in.valid #= false.B
      reset(dut)
      dut.io.in.valid #= true.B
      dut.io.outs.foreach(_.valid.expect(true.B))
    }
  }

  "StreamJoin" should "pair input transactions in order without consuming early arrivals" in {
    simulate(new JoinHarness(3)) { dut =>
      dut.io.ins.foreach { in => in.valid #= false.B; in.bits #= 0.U }
      dut.io.out.ready #= false.B
      reset(dut)
      val random   = new Random(0x701aL)
      val accepted = Array.fill(3)(0)
      val offered  = Array.fill(3)(false)
      var received = 0
      var cycles   = 0
      while (received < 30 && cycles < 600) {
        for (i <- 0 until 3) {
          if (!offered(i) && accepted(i) < 30 && (cycles > 500 || random.nextBoolean())) offered(i) = true
          dut.io.ins(i).valid #= offered(i).B
          dut.io.ins(i).bits #= (i * 64 + accepted(i)).U
        }
        val ready = cycles > 500 || random.nextBoolean()
        val valid = offered.forall(identity)
        dut.io.out.ready #= ready.B
        dut.io.out.valid expect valid.B
        for (i <- 0 until 3) dut.io.ins(i).ready.expect((valid && ready).B)
        if (valid) for (i <- 0 until 3) dut.io.out.bits(i).expect((i * 64 + received).U)
        if (valid && ready) {
          received += 1
          for (i <- 0 until 3) { accepted(i) += 1; offered(i) = false }
        }
        dut.clock.step()
        cycles += 1
      }
      received shouldBe 30
      accepted.toSeq shouldBe Seq(30, 30, 30)
    }
  }

  "Stream payload helpers" should "preserve typed joins, Vec and MixedVec splits, Flow broadcasts and Flow priority" in {
    simulate(new PayloadHarness) { dut =>
      dut.io.addr.valid #= true.B
      dut.io.addr.bits #= 9.U
      dut.io.data.valid #= false.B
      dut.io.data.bits #= (-7).S
      dut.io.joined.ready #= false.B
      dut.io.splitIn.valid #= true.B
      dut.io.splitIn.bits(0) #= 0x12.U
      dut.io.splitIn.bits(1) #= 0x34.U
      dut.io.splitOuts.foreach(_.ready #= false.B)
      dut.io.mixedIn.valid #= true.B
      dut.io.mixedIn.bits(0) #= 0xa.U
      dut.io.mixedIn.bits(1) #= 0x1ab.U
      dut.io.mixedLow.ready #= false.B
      dut.io.mixedHigh.ready #= false.B
      dut.io.mixedA.valid #= true.B
      dut.io.mixedA.bits #= 0xb.U
      dut.io.mixedB.valid #= true.B
      dut.io.mixedB.bits #= true.B
      dut.io.mixedJoin.ready #= false.B
      dut.io.flow.valid #= true.B
      dut.io.flow.bits #= 0xff.U
      dut.io.stream.valid #= true.B
      dut.io.stream.bits #= 0x44.U
      dut.io.priorityIn.valid #= true.B
      dut.io.priorityIn.bits #= 0x55.U
      reset(dut)
      dut.io.joined.valid expect false.B
      dut.io.addr.ready expect false.B
      dut.io.data.valid #= true.B
      dut.clock.step(3)
      dut.io.joined.valid expect true.B
      dut.io.joined.bits.addr expect 9.U
      dut.io.joined.bits.data expect (-7).S
      dut.io.splitOuts(0).bits expect 0x12.U
      dut.io.splitOuts(1).bits expect 0x34.U
      dut.io.mixedLow.bits expect 0xa.U
      dut.io.mixedHigh.bits expect 0x1ab.U
      dut.io.mixedJoin.bits(0).expect(0xb.U)
      dut.io.mixedJoin.bits(1).expect(true.B)
      dut.io.copies.foreach { out => out.valid expect true.B; out.bits expect 0xff.U }
      dut.io.flowLow.bits expect 0xf.U
      dut.io.flowHigh.bits expect 0x100.U
      dut.io.priority.bits expect 0x55.U
      dut.io.stream.ready expect false.B
      dut.io.priorityIn.valid #= false.B
      dut.io.priority.bits expect 0x44.U
      dut.io.stream.ready expect true.B
      dut.io.splitOuts(0).ready #= true.B
      dut.io.mixedLow.ready #= true.B
      dut.clock.step()
      dut.io.splitOuts(0).valid expect false.B
      dut.io.splitOuts(1).valid expect true.B
      dut.io.mixedLow.valid expect false.B
      dut.io.mixedHigh.valid expect true.B
      dut.io.splitIn.ready expect false.B
      dut.io.mixedIn.ready expect false.B
      dut.io.splitOuts(1).ready #= true.B
      dut.io.mixedHigh.ready #= true.B
      dut.io.joined.ready #= true.B
      dut.io.mixedJoin.ready #= true.B
      dut.io.splitIn.ready expect true.B
      dut.io.mixedIn.ready expect true.B
      dut.io.addr.ready expect true.B
      dut.io.data.ready expect true.B
      dut.io.mixedA.ready expect true.B
      dut.io.mixedB.ready expect true.B
    }
  }

  "StreamQueue" should "buffer and flush responses and adapt zero depth without latency" in {
    simulate(new QueueHarness) { dut =>
      dut.io.in.valid #= false.B
      dut.io.in.bits #= 0.U
      dut.io.out.ready #= false.B
      dut.io.flush #= false.B
      dut.io.zeroIn.valid #= true.B
      dut.io.zeroIn.bits #= 0x77.U
      dut.io.zeroOut.ready #= false.B
      reset(dut)
      dut.io.zeroOut.valid expect true.B
      dut.io.zeroOut.bits expect 0x77.U
      dut.io.zeroIn.ready expect false.B
      dut.io.zeroOut.ready #= true.B
      dut.io.zeroIn.ready expect true.B
      for (value <- Seq(0x11, 0x22)) {
        dut.io.in.valid #= true.B
        dut.io.in.bits #= value.U
        dut.io.in.ready expect true.B
        dut.clock.step()
      }
      dut.io.in.bits #= 0x33.U
      dut.io.in.ready expect false.B
      dut.clock.step(3)
      dut.io.out.valid expect true.B
      dut.io.out.bits expect 0x11.U
      dut.io.out.ready #= true.B
      dut.io.in.ready expect true.B
      dut.clock.step()
      dut.io.in.valid #= false.B
      dut.io.out.bits expect 0x22.U
      dut.clock.step()
      dut.io.out.bits expect 0x33.U
      dut.io.out.ready #= false.B
      dut.io.flush #= true.B
      dut.clock.step()
      dut.io.flush #= false.B
      dut.io.out.valid expect false.B
    }
  }

  "Stream components" should "support a single source or destination" in {
    simulate(new RoutingHarness(1, tokens = true)) { dut =>
      dut.io.muxIns(0).valid #= true.B
      dut.io.muxIns(0).bits #= 0x11.U
      dut.io.muxSelect.valid #= true.B
      dut.io.muxSelect.bits #= 0.U
      dut.io.muxOut.ready #= true.B
      dut.io.demuxIn.valid #= true.B
      dut.io.demuxIn.bits #= 0x22.U
      dut.io.demuxSelect.valid #= true.B
      dut.io.demuxSelect.bits #= 0.U
      dut.io.demuxOuts(0).ready #= true.B
      reset(dut)
      dut.io.muxOut.bits expect 0x11.U
      dut.io.muxIns(0).ready expect true.B
      dut.io.demuxOuts(0).bits expect 0x22.U
      dut.io.demuxIn.ready expect true.B
    }
    ChiselStage.emitCHIRRTL(new ArbiterHarness(1, roundRobin = true)) should include("module ArbiterHarness")
    ChiselStage.emitCHIRRTL(new ForkHarness(1)) should include("module ForkHarness")
    ChiselStage.emitCHIRRTL(new JoinHarness(1)) should include("module JoinHarness")
    ChiselStage.emitCHIRRTL(new StreamMux(UInt(8.W), 1)) should include("module StreamMux")
    ChiselStage.emitCHIRRTL(new StreamDemux(UInt(8.W), 1)) should include("module StreamDemux")
  }

  it should "reject empty topologies and invalid queue configuration during elaboration" in {
    for (
      mode <- Seq(
        "arbiter",
        "mux",
        "demux",
        "fork",
        "flow fork",
        "join",
        "queue",
        "zero flush",
        "mux module",
        "demux module",
      )
    ) {
      withClue(s"mode=$mode: ") {
        intercept[IllegalArgumentException] { ChiselStage.emitCHIRRTL(new InvalidHarness(mode)) }
      }
    }
  }
}
