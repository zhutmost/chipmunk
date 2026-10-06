package chipmunk.test.amba

import scala.collection.mutable
import scala.util.Random

import chisel3.*
import circt.stage.ChiselStage

import chipmunk.*
import chipmunk.acorn.AcornOutstanding
import chipmunk.amba.*
import chipmunk.test.ChipmunkFlatSpec

private object AxiToAcornBridgeSpecDut {
  final class ImmediateResponse extends Module {
    val params = AxiParams(32, 16, idWidth = 4)
    val io     = IO(new Bundle {
      val sAxi = Slave(new AxiIO(params))
    })
    private val bridge = Module(new AxiToAcornBridge(params, AcornOutstanding(1, 1)))
    bridge.io.sAxi :<>= io.sAxi
    bridge.io.mAcorn.rd.cmd.ready      := bridge.io.mAcorn.rd.rsp.ready
    bridge.io.mAcorn.rd.rsp.valid      := bridge.io.mAcorn.rd.cmd.valid
    bridge.io.mAcorn.rd.rsp.bits.data  := bridge.io.mAcorn.rd.cmd.bits.addr
    bridge.io.mAcorn.rd.rsp.bits.error := false.B
    bridge.io.mAcorn.wr.cmd.ready      := bridge.io.mAcorn.wr.rsp.ready
    bridge.io.mAcorn.wr.rsp.valid      := bridge.io.mAcorn.wr.cmd.valid
    bridge.io.mAcorn.wr.rsp.bits.error := bridge.io.mAcorn.wr.cmd.bits.data(0)
  }
}

final class AxiToAcornBridgeSpec extends ChipmunkFlatSpec {
  private case class Burst(
    address: BigInt,
    beats: Int,
    burst: AxiBurstType.Type,
    id: BigInt,
    size: Int,
    supported: Boolean = true,
    errors: Set[Int] = Set.empty,
    exclusive: Boolean = false,
  )
  private case class ReadBeat(
    address: BigInt,
    data: BigInt,
    error: Boolean,
    last: Boolean,
    id: BigInt,
    supported: Boolean,
  )
  private case class WriteBeat(
    address: BigInt,
    data: BigInt,
    strobe: BigInt,
    last: Boolean,
    supported: Boolean,
    error: Boolean,
  )
  private case class ReadResponse(due: Int, data: BigInt, error: Boolean)
  private case class WriteResponse(due: Int, error: Boolean)

  private def addresses(burst: Burst, bytes: Int): Seq[BigInt] = {
    val span = BigInt(bytes) * burst.beats
    val base = (burst.address / span) * span
    (0 until burst.beats).map { beat =>
      burst.burst.litValue.toInt match {
        case 0     => burst.address
        case 1     => burst.address + beat * bytes
        case 2     => base + (burst.address - base + beat * bytes) % span
        case other => throw new IllegalArgumentException(s"Unexpected AXI burst encoding $other")
      }
    }
  }

  private def traffic(params: AxiParams, smallRange: Boolean): Seq[Burst] = {
    def id(index: Int): BigInt =
      if (!params.hasId) BigInt(0)
      else ((BigInt(1) << (params.idWidth - 1)) + index) & ((BigInt(1) << params.idWidth) - 1)
    val bytes = params.strobeWidth
    val full  = params.fullSize
    if (smallRange) {
      Seq(
        Burst(24, 1, AxiBurstType.BURST_INCR, id(0), full),
        Burst(24, 2, AxiBurstType.BURST_INCR, id(1), full, supported = false),
        Burst(0, 4, AxiBurstType.BURST_WRAP, id(2), full),
      )
    } else {
      val bursts = Seq(
        Burst(0, 16, AxiBurstType.BURST_INCR, id(0), full, errors = Set(4)),
        Burst(0x1000, 16, AxiBurstType.BURST_FIXED, id(1), full),
        Burst(0x2000, math.min(256, 4096 / bytes), AxiBurstType.BURST_INCR, id(2), full, errors = Set(0)),
        Burst(0x3000, 4, AxiBurstType.BURST_INCR, id(3), full, errors = Set(3)),
        Burst(0x3400, 1, AxiBurstType.BURST_INCR, id(4), full, exclusive = true),
      ) ++ Seq(2, 4, 8, 16).zipWithIndex.map { case (beats, index) =>
        // Start at the last word of a 4KB page: WRAP must return to its own window.
        Burst(0x4000 - bytes, beats, AxiBurstType.BURST_WRAP, id(index + 5), full)
      }
      if (full == 0) bursts
      else
        bursts ++ Seq(
          Burst(0x5000, 5, AxiBurstType.BURST_INCR, id(9), full - 1, supported = false),
          Burst(0x5101, 3, AxiBurstType.BURST_INCR, id(10), full, supported = false),
          Burst(0x5200, 1, AxiBurstType.BURST_INCR, id(11), full),
        )
    }
  }

  private def runTraffic(params: AxiParams, capacity: AcornOutstanding, wFirst: Boolean, smallRange: Boolean): Unit = {
    val bursts                   = traffic(params, smallRange)
    val dataMask                 = (BigInt(1) << params.dataWidth) - 1
    def data(index: Int): BigInt = ((BigInt(1) << (params.dataWidth - 1)) | (BigInt(index) * 37 + 11)) & dataMask
    var readCommandIndex         = 0
    val expectedReads            = bursts.flatMap { burst =>
      addresses(burst, params.strobeWidth).zipWithIndex.map { case (address, beat) =>
        val value = if (burst.supported) {
          val value = data(readCommandIndex)
          readCommandIndex += 1
          value
        } else BigInt(0)
        ReadBeat(
          address,
          value,
          !burst.supported || burst.errors(beat),
          beat == burst.beats - 1,
          burst.id,
          burst.supported,
        )
      }
    }
    val readCommands   = expectedReads.filter(_.supported)
    val expectedWrites = bursts
      .flatMap { burst =>
        addresses(burst, params.strobeWidth).zipWithIndex.map { case (address, beat) =>
          (burst, address, beat)
        }
      }
      .zipWithIndex
      .map { case ((burst, address, beat), index) =>
        val strobe = index % 3 match {
          case 0 => BigInt(0)
          case 1 => BigInt(1) << (index % params.strobeWidth)
          case 2 => (BigInt(1) << params.strobeWidth) - 1
        }
        WriteBeat(address, data(index), strobe, beat == burst.beats - 1, burst.supported, burst.errors(beat))
      }
    val writeCommands = expectedWrites.filter(_.supported)
    val writeEnds     = bursts.scanLeft(0)((total, burst) => total + (if (burst.supported) burst.beats else 0)).tail

    simulate(new AxiToAcornBridge(params, capacity)) { dut =>
      val random = new Random(20261006)
      val reads  = mutable.Queue.empty[ReadResponse]
      val writes = mutable.Queue.empty[WriteResponse]
      var awSent, arSent, wSent, readIssued, writeIssued, readReturned, writeReturned, rDelivered, bDelivered = 0
      var readReserved, writePending, peakReads                                                               = 0
      var cycle                                                                                               = 0
      var bWait                                                                                               = 0
      var heldRead, heldWrite, heldR, heldB = Option.empty[Seq[BigInt]]

      def bool(value: Bool): Boolean                                                   = value.peek().litToBoolean
      def held(previous: Option[Seq[BigInt]], valid: Bool, payload: Seq[BigInt]): Unit =
        previous.foreach { expected =>
          assert(bool(valid), "VALID was withdrawn under backpressure")
          assert(payload == expected, "Payload changed under backpressure")
        }
      def driveAddress(channel: AxiWriteAddrChannel, burst: Burst): Unit = {
        channel.addr.poke(burst.address.U)
        channel.len.poke((burst.beats - 1).U)
        channel.size.poke(AxiBurstSize.all(burst.size))
        channel.burst.poke(burst.burst)
        channel.id.foreach(_.poke(burst.id.U))
        channel.lock.poke((if (burst.exclusive) 1 else 0).U)
        channel.prot.poke(7.U)
        channel.cache.poke(3.U)
        channel.qos.foreach(_.poke(9.U))
        channel.region.foreach(_.poke(5.U))
      }

      while (rDelivered < expectedReads.size || bDelivered < bursts.size) {
        assert(cycle < 15000, "Transactions did not complete")
        dut.io.sAxi.ar.valid.poke((arSent < bursts.size).B)
        driveAddress(dut.io.sAxi.ar.bits, bursts(math.min(arSent, bursts.size - 1)))
        dut.io.sAxi.aw.valid.poke((awSent < bursts.size && (!wFirst || cycle >= 8)).B)
        driveAddress(dut.io.sAxi.aw.bits, bursts(math.min(awSent, bursts.size - 1)))
        dut.io.sAxi.w.valid.poke((wSent < expectedWrites.size && (wFirst || cycle >= 8)).B)
        val offeredWrite = expectedWrites(math.min(wSent, expectedWrites.size - 1))
        dut.io.sAxi.w.bits.data.poke(offeredWrite.data.U)
        dut.io.sAxi.w.bits.strb.poke(offeredWrite.strobe.U)
        dut.io.sAxi.w.bits.last.poke(offeredWrite.last.B)

        val readReady  = random.nextBoolean()
        val writeReady = random.nextBoolean()
        dut.io.mAcorn.rd.cmd.ready.poke(readReady.B)
        dut.io.mAcorn.wr.cmd.ready.poke(writeReady.B)
        val readValid  = reads.headOption.exists(_.due <= cycle)
        val writeValid = writes.headOption.exists(_.due <= cycle)
        dut.io.mAcorn.rd.rsp.valid.poke(readValid.B)
        dut.io.mAcorn.rd.rsp.bits.data.poke(reads.headOption.fold(BigInt(0))(_.data).U)
        dut.io.mAcorn.rd.rsp.bits.error.poke(reads.headOption.exists(_.error).B)
        dut.io.mAcorn.wr.rsp.valid.poke(writeValid.B)
        dut.io.mAcorn.wr.rsp.bits.error.poke(writes.headOption.exists(_.error).B)

        val rReady = cycle >= 180 && random.nextBoolean()
        dut.io.sAxi.r.ready.poke(rReady.B)
        // Model a master that waits for BVALID before ever asserting BREADY.
        val bValid = bool(dut.io.sAxi.b.valid)
        if (bValid) bWait += 1 else bWait = 0
        val bReady = bWait >= 12 && random.nextBoolean()
        dut.io.sAxi.b.ready.poke(bReady.B)

        val readPayload  = Seq(dut.io.mAcorn.rd.cmd.bits.addr.peek().litValue)
        val writePayload = Seq(
          dut.io.mAcorn.wr.cmd.bits.addr.peek().litValue,
          dut.io.mAcorn.wr.cmd.bits.data.peek().litValue,
          dut.io.mAcorn.wr.cmd.bits.strobe.peek().litValue,
        )
        val rPayload = Seq(
          dut.io.sAxi.r.bits.data.peek().litValue,
          dut.io.sAxi.r.bits.resp.peek().litValue,
          dut.io.sAxi.r.bits.last.peek().litValue,
        ) ++ dut.io.sAxi.r.bits.id.toSeq.map(_.peek().litValue)
        val bPayload =
          Seq(dut.io.sAxi.b.bits.resp.peek().litValue) ++ dut.io.sAxi.b.bits.id.toSeq.map(_.peek().litValue)
        held(heldRead, dut.io.mAcorn.rd.cmd.valid, readPayload)
        held(heldWrite, dut.io.mAcorn.wr.cmd.valid, writePayload)
        held(heldR, dut.io.sAxi.r.valid, rPayload)
        held(heldB, dut.io.sAxi.b.valid, bPayload)

        val awFire            = bool(dut.io.sAxi.aw.valid) && bool(dut.io.sAxi.aw.ready)
        val arFire            = bool(dut.io.sAxi.ar.valid) && bool(dut.io.sAxi.ar.ready)
        val wFire             = bool(dut.io.sAxi.w.valid) && bool(dut.io.sAxi.w.ready)
        val readFire          = bool(dut.io.mAcorn.rd.cmd.valid) && readReady
        val writeFire         = bool(dut.io.mAcorn.wr.cmd.valid) && writeReady
        val readResponseFire  = readValid && bool(dut.io.mAcorn.rd.rsp.ready)
        val writeResponseFire = writeValid && bool(dut.io.mAcorn.wr.rsp.ready)
        val rFire             = bool(dut.io.sAxi.r.valid) && rReady
        val bFire             = bValid && bReady

        if (readFire) {
          assert(readIssued < readCommands.size, "An extra Acorn read was issued")
          dut.io.mAcorn.rd.cmd.bits.addr.expect(readCommands(readIssued).address.U)
        }
        if (writeFire) {
          assert(writeIssued < writeCommands.size, "An extra Acorn write was issued")
          val expected = writeCommands(writeIssued)
          dut.io.mAcorn.wr.cmd.bits.addr.expect(expected.address.U)
          dut.io.mAcorn.wr.cmd.bits.data.expect(expected.data.U)
          dut.io.mAcorn.wr.cmd.bits.strobe.expect(expected.strobe.U)
        }
        if (rFire) {
          val expected = expectedReads(rDelivered)
          dut.io.sAxi.r.bits.data.expect(expected.data.U)
          dut.io.sAxi.r.bits.resp.expect(if (expected.error) AxiResp.RESP_SLVERR else AxiResp.RESP_OKAY)
          dut.io.sAxi.r.bits.last.expect(expected.last.B)
          dut.io.sAxi.r.bits.id.foreach(_.expect(expected.id.U))
        }
        if (bValid) {
          assert(writeReturned >= writeEnds(bDelivered), "BVALID preceded the final Acorn write response")
          val burst = bursts(bDelivered)
          dut.io.sAxi.b.bits.resp
            .expect(if (!burst.supported || burst.errors.nonEmpty) AxiResp.RESP_SLVERR else AxiResp.RESP_OKAY)
          dut.io.sAxi.b.bits.id.foreach(_.expect(burst.id.U))
        }

        heldRead = Option.when(bool(dut.io.mAcorn.rd.cmd.valid) && !readReady)(readPayload)
        heldWrite = Option.when(bool(dut.io.mAcorn.wr.cmd.valid) && !writeReady)(writePayload)
        heldR = Option.when(bool(dut.io.sAxi.r.valid) && !rReady)(rPayload)
        heldB = Option.when(bValid && !bReady)(bPayload)
        dut.clock.step()

        if (awFire) awSent += 1
        if (arFire) arSent += 1
        if (wFire) wSent += 1
        if (readResponseFire) { reads.dequeue(); readReturned += 1 }
        if (writeResponseFire) { writes.dequeue(); writeReturned += 1; writePending -= 1 }
        if (readFire) {
          val expected = readCommands(readIssued)
          reads.enqueue(ReadResponse(cycle + 7 + random.nextInt(9), expected.data, expected.error))
          readIssued += 1
          readReserved += 1
        }
        if (writeFire) {
          writes.enqueue(WriteResponse(cycle + 7 + random.nextInt(9), writeCommands(writeIssued).error))
          writeIssued += 1
          writePending += 1
        }
        if (rFire) {
          if (expectedReads(rDelivered).supported) readReserved -= 1
          rDelivered += 1
        }
        if (bFire) bDelivered += 1
        assert(readReserved >= 0 && readReserved <= capacity.read, "Read response reservations exceeded capacity")
        assert(writePending >= 0 && writePending <= capacity.write, "Pending writes exceeded capacity")
        peakReads = math.max(peakReads, readReserved)
        cycle += 1
      }
      assert(arSent == bursts.size && awSent == bursts.size && wSent == expectedWrites.size)
      assert(readIssued == readCommands.size && readReturned == readCommands.size)
      assert(writeIssued == writeCommands.size && writeReturned == writeCommands.size)
      assert(reads.isEmpty && writes.isEmpty && readReserved == 0 && writePending == 0)
      if (!smallRange) assert(peakReads == capacity.read, "Read credits were not fully exercised")
    }
  }

  for (
    (params, capacity) <- Seq(
      (AxiParams(32, 16), AcornOutstanding(1, 1)),
      (AxiParams(64, 16, idWidth = 40, hasQos = true, hasRegion = true), AcornOutstanding(3, 5)),
      (AxiParams(128, 16, idWidth = 4), AcornOutstanding(4, 4)),
    );
    wFirst <- Seq(true, false)
  ) {
    s"AxiToAcornBridge(${params.dataWidth}, W first = $wFirst)" should
      "split bursts and preserve responses under independent stalls" in {
        runTraffic(params, capacity, wFirst, smallRange = false)
      }
  }

  for (width <- Seq(8, 1024)) {
    s"AxiToAcornBridge($width)" should "support the AXI data-width limits" in {
      runTraffic(AxiParams(width, 16), AcornOutstanding(), wFirst = true, smallRange = false)
    }
  }

  "AxiToAcornBridge" should "reject address overflow without issuing truncated Acorn commands" in {
    runTraffic(AxiParams(64, 5, idWidth = 4), AcornOutstanding(3, 2), wFirst = false, smallRange = true)
  }

  it should "accept Acorn responses in the same cycle as commands" in {
    simulate(new AxiToAcornBridgeSpecDut.ImmediateResponse) { dut =>
      def address(channel: AxiWriteAddrChannel, id: Int): Unit = {
        channel.addr.poke(0x20.U)
        channel.len.poke(3.U)
        channel.size.poke(AxiBurstSize.SIZE4)
        channel.burst.poke(AxiBurstType.BURST_INCR)
        channel.id.foreach(_.poke(id.U))
        channel.lock.poke(0.U)
        channel.prot.poke(0.U)
        channel.cache.poke(0.U)
      }
      dut.io.sAxi.aw.valid.poke(true.B)
      dut.io.sAxi.ar.valid.poke(true.B)
      address(dut.io.sAxi.aw.bits, 2)
      address(dut.io.sAxi.ar.bits, 3)
      dut.io.sAxi.w.valid.poke(false.B)
      dut.io.sAxi.w.bits.data.poke(0.U)
      dut.io.sAxi.w.bits.strb.poke(15.U)
      dut.io.sAxi.w.bits.last.poke(false.B)
      dut.io.sAxi.r.ready.poke(false.B)
      dut.io.sAxi.b.ready.poke(false.B)
      dut.clock.step()
      dut.io.sAxi.aw.valid.poke(false.B)
      dut.io.sAxi.ar.valid.poke(false.B)
      var written, read = 0
      var complete      = false
      var bReturned     = false
      for (cycle <- 0 until 60 if !complete) {
        dut.io.sAxi.w.valid.poke((written < 4).B)
        dut.io.sAxi.w.bits.data.poke((if (written == 3) 1 else 0).U)
        dut.io.sAxi.w.bits.last.poke((written == 3).B)
        dut.io.sAxi.r.ready.poke((cycle >= 8).B)
        val bValid = dut.io.sAxi.b.valid.peek().litToBoolean
        dut.io.sAxi.b.ready.poke(bValid.B)
        val wFire = written < 4 && dut.io.sAxi.w.ready.peek().litToBoolean
        val rFire = cycle >= 8 && dut.io.sAxi.r.valid.peek().litToBoolean
        if (rFire) {
          dut.io.sAxi.r.bits.data.expect((0x20 + read * 4).U)
          dut.io.sAxi.r.bits.last.expect((read == 3).B)
          dut.io.sAxi.r.bits.resp.expect(AxiResp.RESP_OKAY)
          dut.io.sAxi.r.bits.id.get.expect(3.U)
        }
        if (bValid) {
          dut.io.sAxi.b.bits.resp.expect(AxiResp.RESP_SLVERR)
          dut.io.sAxi.b.bits.id.get.expect(2.U)
        }
        dut.clock.step()
        if (wFire) written += 1
        if (rFire) read += 1
        if (bValid) bReturned = true
        complete = bReturned && read == 4
      }
      assert(written == 4 && read == 4 && complete)
    }
  }

  it should "reject address widths that cannot represent a whole word" in {
    intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(new AxiToAcornBridge(AxiParams(64, 2)))
    }
  }
}
