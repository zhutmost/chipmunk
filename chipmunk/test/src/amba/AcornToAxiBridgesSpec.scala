package chipmunk.test.amba

import scala.collection.mutable
import scala.util.Random

import chisel3.*
import circt.stage.ChiselStage

import chipmunk.*
import chipmunk.acorn.{AcornIO, AcornParams}
import chipmunk.amba.*
import chipmunk.test.ChipmunkFlatSpec

private object AcornToAxiBridgesSpecDut {
  final class Harness(params: AxiParams, lite: Boolean, writeId: BigInt, readId: BigInt, writeProt: Int, readProt: Int)
      extends Module {
    val io = IO(new Bundle {
      val sAcorn = Slave(new AcornIO(AcornParams(params.dataWidth, params.addrWidth)))
      val mAxi   = Master(new AxiIO(params))
    })
    if (lite) {
      val bridge = Module(
        new AcornToAxiLiteBridge(AxiLiteParams(params.dataWidth, params.addrWidth), writeProt, readProt)
      )
      val adapter = Module(new AxiLiteToAxiBridge(params, writeId, readId))
      bridge.io.sAcorn :<>= io.sAcorn
      adapter.io.sAxiLite :<>= bridge.io.mAxiLite
      io.mAxi :<>= adapter.io.mAxi
    } else {
      val bridge = Module(new AcornToAxiBridge(params, writeId, readId, writeProt, readProt))
      bridge.io.sAcorn :<>= io.sAcorn
      io.mAxi :<>= bridge.io.mAxi
    }
  }
}

final class AcornToAxiBridgesSpec extends ChipmunkFlatSpec {
  private case class Request(address: BigInt, data: BigInt, strobe: BigInt, response: AxiResp.Type, aligned: Boolean)
  private case class ReadResponse(due: Int, data: BigInt, response: AxiResp.Type)
  private case class WriteResponse(due: Int, response: AxiResp.Type)
  private val responses = Seq(AxiResp.RESP_OKAY, AxiResp.RESP_SLVERR, AxiResp.RESP_DECERR)

  private def runTraffic(params: AxiParams, lite: Boolean, wFirst: Boolean): Unit = {
    val writeId   = if (params.hasId) (BigInt(1) << (params.idWidth - 1)) + 7 else BigInt(0)
    val readId    = if (params.hasId) BigInt(5) else BigInt(0)
    val writeProt = if (params.hasId) 5 else 0
    val readProt  = if (params.hasId) 7 else 0
    val requests  = (0 until 24).map { index =>
      val aligned = params.strobeWidth == 1 || !Set(5, 12, 21)(index)
      val address = BigInt((index + 8) * params.strobeWidth + (if (aligned) 0 else 1))
      val mask    = (BigInt(1) << params.dataWidth) - 1
      val data    = ((BigInt(1) << (params.dataWidth - 1)) | (BigInt(index) * 23 + 3)) & mask
      val strobe  = index % 3 match {
        case 0 => BigInt(0)
        case 1 => BigInt(1) << (index % params.strobeWidth)
        case 2 => (BigInt(1) << params.strobeWidth) - 1
      }
      Request(address, data, strobe, responses(index % responses.size), aligned)
    }
    val forwarded = requests.filter(_.aligned)

    simulate(new AcornToAxiBridgesSpecDut.Harness(params, lite, writeId, readId, writeProt, readProt)) { dut =>
      val random         = new Random(20261006)
      val readResponses  = mutable.Queue.empty[ReadResponse]
      val writeResponses = mutable.Queue.empty[WriteResponse]
      var readSent, writeSent, awReceived, wReceived, arReceived, bScheduled, bReturned, rReturned = 0
      var readDelivered, writeDelivered, cycle, readWait, writeWait                                = 0
      var capturedReadUnderStall, capturedWriteUnderStall, readCompletedBeforeWrite                = false
      var heldAw, heldW, heldAr, heldRead, heldWrite = Option.empty[Seq[BigInt]]

      def bool(value: Bool): Boolean                                                   = value.peek().litToBoolean
      def held(previous: Option[Seq[BigInt]], valid: Bool, payload: Seq[BigInt]): Unit =
        previous.foreach { expected =>
          assert(bool(valid), "VALID was withdrawn under backpressure")
          assert(payload == expected, "Payload changed under backpressure")
        }
      def sampleAddress(bits: AxiWriteAddrChannel): Seq[BigInt] =
        Seq(bits.addr, bits.prot, bits.size, bits.len, bits.burst, bits.lock, bits.cache).map(_.peek().litValue) ++
          bits.id.toSeq.map(_.peek().litValue) ++ bits.qos.toSeq.map(_.peek().litValue) ++
          bits.region.toSeq.map(_.peek().litValue)
      def checkAddress(bits: AxiWriteAddrChannel, request: Request, id: BigInt, prot: Int): Unit = {
        bits.addr.expect(request.address.U)
        bits.prot.expect(prot.U)
        bits.len.expect(0.U)
        assert(bits.size.peek().litValue == params.fullSize)
        bits.burst.expect(AxiBurstType.BURST_INCR)
        bits.lock.expect(0.U)
        bits.cache.expect(0.U)
        bits.id.foreach(_.expect(id.U))
        bits.qos.foreach(_.expect(0.U))
        bits.region.foreach(_.expect(0.U))
      }

      while (readDelivered < requests.size || writeDelivered < requests.size) {
        assert(cycle < 5000, "Traffic did not complete")
        val readRequest  = requests(math.min(readSent, requests.size - 1))
        val writeRequest = requests(math.min(writeSent, requests.size - 1))
        // Once accepted, the producer offers a different payload while the bridge is still busy.
        dut.io.sAcorn.rd.cmd.valid.poke((readSent < requests.size).B)
        dut.io.sAcorn.rd.cmd.bits.addr.poke(readRequest.address.U)
        dut.io.sAcorn.wr.cmd.valid.poke((writeSent < requests.size).B)
        dut.io.sAcorn.wr.cmd.bits.addr.poke(writeRequest.address.U)
        dut.io.sAcorn.wr.cmd.bits.data.poke(writeRequest.data.U)
        dut.io.sAcorn.wr.cmd.bits.strobe.poke(writeRequest.strobe.U)

        val awReady = (!wFirst || cycle >= 35) && random.nextBoolean()
        val wReady  = (wFirst || cycle >= 35) && random.nextBoolean()
        val arReady = random.nextBoolean()
        dut.io.mAxi.aw.ready.poke(awReady.B)
        dut.io.mAxi.w.ready.poke(wReady.B)
        dut.io.mAxi.ar.ready.poke(arReady.B)
        val bValid = writeResponses.headOption.exists(_.due <= cycle)
        val rValid = readResponses.headOption.exists(_.due <= cycle)
        dut.io.mAxi.b.valid.poke(bValid.B)
        dut.io.mAxi.b.bits.resp.poke(writeResponses.headOption.fold(AxiResp.RESP_OKAY)(_.response))
        dut.io.mAxi.b.bits.id.foreach(_.poke(writeId.U))
        dut.io.mAxi.r.valid.poke(rValid.B)
        dut.io.mAxi.r.bits.data.poke(readResponses.headOption.fold(BigInt(0))(_.data).U)
        dut.io.mAxi.r.bits.resp.poke(readResponses.headOption.fold(AxiResp.RESP_OKAY)(_.response))
        dut.io.mAxi.r.bits.id.foreach(_.poke(readId.U))
        dut.io.mAxi.r.bits.last.poke(true.B)

        val readValid  = bool(dut.io.sAcorn.rd.rsp.valid)
        val writeValid = bool(dut.io.sAcorn.wr.rsp.valid)
        if (readValid) readWait += 1 else readWait = 0
        if (writeValid) writeWait += 1 else writeWait = 0
        // Responses must become valid and be captured even when Acorn initially holds READY low.
        val readReady  = cycle >= 80 && readWait >= 5 && random.nextBoolean()
        val writeReady = cycle >= 100 && writeWait >= 5 && random.nextBoolean()
        dut.io.sAcorn.rd.rsp.ready.poke(readReady.B)
        dut.io.sAcorn.wr.rsp.ready.poke(writeReady.B)

        val awPayload = sampleAddress(dut.io.mAxi.aw.bits)
        val arPayload = sampleAddress(dut.io.mAxi.ar.bits)
        val wPayload  = Seq(
          dut.io.mAxi.w.bits.data.peek().litValue,
          dut.io.mAxi.w.bits.strb.peek().litValue,
          dut.io.mAxi.w.bits.last.peek().litValue,
        )
        val readPayload =
          Seq(dut.io.sAcorn.rd.rsp.bits.data.peek().litValue, dut.io.sAcorn.rd.rsp.bits.error.peek().litValue)
        val writePayload = Seq(dut.io.sAcorn.wr.rsp.bits.error.peek().litValue)
        held(heldAw, dut.io.mAxi.aw.valid, awPayload)
        held(heldAr, dut.io.mAxi.ar.valid, arPayload)
        held(heldW, dut.io.mAxi.w.valid, wPayload)
        held(heldRead, dut.io.sAcorn.rd.rsp.valid, readPayload)
        held(heldWrite, dut.io.sAcorn.wr.rsp.valid, writePayload)

        val readCmdFire  = bool(dut.io.sAcorn.rd.cmd.valid) && bool(dut.io.sAcorn.rd.cmd.ready)
        val writeCmdFire = bool(dut.io.sAcorn.wr.cmd.valid) && bool(dut.io.sAcorn.wr.cmd.ready)
        val awFire       = bool(dut.io.mAxi.aw.valid) && awReady
        val wFire        = bool(dut.io.mAxi.w.valid) && wReady
        val arFire       = bool(dut.io.mAxi.ar.valid) && arReady
        val bFire        = bValid && bool(dut.io.mAxi.b.ready)
        val rFire        = rValid && bool(dut.io.mAxi.r.ready)
        val readRspFire  = readValid && readReady
        val writeRspFire = writeValid && writeReady

        if (awFire) {
          assert(awReceived < forwarded.size, "An extra AXI AW was sent")
          checkAddress(dut.io.mAxi.aw.bits, forwarded(awReceived), writeId, writeProt)
        }
        if (wFire) {
          assert(wReceived < forwarded.size, "An extra AXI W was sent")
          dut.io.mAxi.w.bits.data.expect(forwarded(wReceived).data.U)
          dut.io.mAxi.w.bits.strb.expect(forwarded(wReceived).strobe.U)
          dut.io.mAxi.w.bits.last.expect(true.B)
        }
        if (arFire) {
          assert(arReceived < forwarded.size, "An extra AXI AR was sent")
          checkAddress(dut.io.mAxi.ar.bits, forwarded(arReceived), readId, readProt)
        }
        if (readValid) {
          val expected = requests(readDelivered)
          dut.io.sAcorn.rd.rsp.bits.data.expect((if (expected.aligned) expected.data else BigInt(0)).U)
          dut.io.sAcorn.rd.rsp.bits.error.expect((!expected.aligned || expected.response != AxiResp.RESP_OKAY).B)
          dut.io.sAcorn.rd.cmd.ready.expect(false.B)
        }
        if (writeValid) {
          val expected = requests(writeDelivered)
          dut.io.sAcorn.wr.rsp.bits.error.expect((!expected.aligned || expected.response != AxiResp.RESP_OKAY).B)
          dut.io.sAcorn.wr.cmd.ready.expect(false.B)
        }

        heldAw = Option.when(bool(dut.io.mAxi.aw.valid) && !awReady)(awPayload)
        heldW = Option.when(bool(dut.io.mAxi.w.valid) && !wReady)(wPayload)
        heldAr = Option.when(bool(dut.io.mAxi.ar.valid) && !arReady)(arPayload)
        heldRead = Option.when(readValid && !readReady)(readPayload)
        heldWrite = Option.when(writeValid && !writeReady)(writePayload)
        dut.clock.step()

        if (readCmdFire) readSent += 1
        if (writeCmdFire) writeSent += 1
        if (awFire) awReceived += 1
        if (wFire) wReceived += 1
        if (arFire) {
          val expected = forwarded(arReceived)
          readResponses.enqueue(ReadResponse(cycle + 3 + random.nextInt(4), expected.data, expected.response))
          arReceived += 1
        }
        if (rFire) {
          readResponses.dequeue()
          rReturned += 1
          capturedReadUnderStall ||= !readReady
        }
        if (bFire) {
          writeResponses.dequeue()
          bReturned += 1
          capturedWriteUnderStall ||= !writeReady
        }
        while (bScheduled < math.min(awReceived, wReceived)) {
          writeResponses.enqueue(WriteResponse(cycle + 3 + random.nextInt(4), forwarded(bScheduled).response))
          bScheduled += 1
        }
        if (readRspFire) {
          readDelivered += 1
          readCompletedBeforeWrite ||= writeDelivered == 0
        }
        if (writeRspFire) writeDelivered += 1
        assert(readSent - readDelivered >= 0 && readSent - readDelivered <= 1)
        assert(writeSent - writeDelivered >= 0 && writeSent - writeDelivered <= 1)
        cycle += 1
      }
      assert(readSent == requests.size && writeSent == requests.size)
      assert(awReceived == forwarded.size && wReceived == forwarded.size && arReceived == forwarded.size)
      assert(bReturned == forwarded.size && rReturned == forwarded.size)
      assert(readResponses.isEmpty && writeResponses.isEmpty)
      assert(capturedReadUnderStall && capturedWriteUnderStall, "AXI responses did not enter the local buffers")
      assert(readCompletedBeforeWrite, "Read progress was coupled to the stalled write")
    }
  }

  for (
    lite   <- Seq(false, true);
    params <- Seq(AxiParams(32, 16), AxiParams(64, 16, idWidth = 40, hasQos = true, hasRegion = true));
    wFirst <- Seq(true, false)
  ) {
    s"AcornTo${if (lite) "AxiLite" else "Axi"}Bridge(${params.dataWidth}, W first = $wFirst)" should
      "preserve transactions through independent stalls and local alignment errors" in {
        runTraffic(params, lite, wFirst)
      }
  }

  for (width <- Seq(8, 128, 1024)) {
    s"AcornToAxiBridge($width)" should "support AXI data widths beyond AXI-Lite" in {
      runTraffic(AxiParams(width, 16), lite = false, wFirst = true)
    }
  }

  "AcornToAxiBridge" should "validate ID widths, PROT values, and address geometry" in {
    for (
      (params, writeId, readId, writeProt, readProt) <- Seq(
        (AxiParams(32, 16), BigInt(1), BigInt(0), 0, 0),
        (AxiParams(32, 16), BigInt(0), BigInt(1), 0, 0),
        (AxiParams(32, 16, idWidth = 4), BigInt(-1), BigInt(0), 0, 0),
        (AxiParams(32, 16, idWidth = 4), BigInt(0), BigInt(16), 0, 0),
        (AxiParams(32, 16), BigInt(0), BigInt(0), 8, 0),
        (AxiParams(32, 16), BigInt(0), BigInt(0), 0, -1),
        (AxiParams(64, 2), BigInt(0), BigInt(0), 0, 0),
      )
    ) {
      intercept[IllegalArgumentException] {
        ChiselStage.emitCHIRRTL(new AcornToAxiBridge(params, writeId, readId, writeProt, readProt))
      }
    }
  }

  "AcornToAxiLiteBridge" should "validate its static PROT settings through the shared controller" in {
    intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(new AcornToAxiLiteBridge(AxiLiteParams(32, 16), readProt = 8))
    }
  }
}
