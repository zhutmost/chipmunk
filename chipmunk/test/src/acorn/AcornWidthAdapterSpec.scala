package chipmunk.test.acorn

import scala.collection.mutable
import scala.util.Random

import chisel3.*
import circt.stage.ChiselStage

import chipmunk.acorn.*
import chipmunk.test.ChipmunkFlatSpec

final class AcornWidthAdapterSpec extends ChipmunkFlatSpec {
  private case class Request(address: BigInt, data: BigInt, strobe: BigInt, aligned: Boolean)
  private case class Segment(address: BigInt, data: BigInt, strobe: BigInt, error: Boolean)
  private case class Response(due: Int, data: BigInt, error: Boolean)

  private def memoryWord(address: BigInt, bytes: Int): BigInt =
    (0 until bytes).foldLeft(BigInt(0))((value, lane) => value | (((address + lane) * 17 + 53) & 255) << (8 * lane))

  private def runTraffic(inputWidth: Int, outputWidth: Int): Unit = {
    val input    = AcornParams(inputWidth, 12)
    val output   = AcornParams(outputWidth, 12)
    val split    = inputWidth > outputWidth
    val count    = if (split) inputWidth / outputWidth else 1
    val requests = (0 until 24).map { index =>
      val aligned = input.bytesPerWord == 1 || !Set(3, 7, 19)(index)
      val address =
        if (index == 23) BigInt(4096 - input.bytesPerWord)
        else BigInt((index + 8) * input.bytesPerWord + (if (aligned) 0 else 1))
      val strobe = index % 3 match {
        case 0 => BigInt(0)
        case 1 => BigInt(1) << (index % input.bytesPerWord)
        case 2 => (BigInt(1) << input.bytesPerWord) - 1
      }
      Request(address, memoryWord(address + 123, input.bytesPerWord), strobe, aligned)
    }

    def converted(request: Request, index: Int): Seq[Segment] = {
      if (!request.aligned) Seq.empty
      else {
        val failure =
          if (index % 4 != 1) -1
          else
            (index / 4) % 3 match {
              case 0 => 0
              case 1 => count / 2
              case 2 => count - 1
            }
        (0 until count).take(if (failure < 0) count else failure + 1).map { segment =>
          val address =
            if (split) request.address + segment * output.bytesPerWord
            else request.address & ~(BigInt(output.bytesPerWord) - 1)
          val byteOffset = if (split) segment * output.bytesPerWord else 0
          val laneOffset = if (inputWidth < outputWidth) (request.address - address).toInt else 0
          val dataMask   = (BigInt(1) << outputWidth) - 1
          val strobeMask = (BigInt(1) << output.bytesPerWord) - 1
          Segment(
            address,
            ((request.data >> (8 * byteOffset)) << (8 * laneOffset)) & dataMask,
            ((request.strobe >> byteOffset) << laneOffset) & strobeMask,
            segment == failure,
          )
        }
      }
    }
    val readParts      = requests.zipWithIndex.map((request, index) => converted(request, index))
    val writeParts     = requests.zipWithIndex.map((request, index) => converted(request, index))
    val expectedReads  = readParts.flatten
    val expectedWrites = writeParts.flatten

    simulate(new AcornWidthAdapter(input, output)) { dut =>
      val random                        = new Random(20261006)
      val readResponses, writeResponses = mutable.Queue.empty[Response]
      var readSent, writeSent, readReceived, writeReceived, readDelivered, writeDelivered, cycle = 0
      var readWait, writeWait                                                                    = 0
      var capturedRead, capturedWrite, readBeforeWrite                                           = false
      var heldReadCmd, heldWriteCmd, heldReadRsp, heldWriteRsp = Option.empty[Seq[BigInt]]

      def bool(value: Bool): Boolean                                                        = value.peek().litToBoolean
      def checkHeld(previous: Option[Seq[BigInt]], valid: Bool, payload: Seq[BigInt]): Unit =
        previous.foreach { expected =>
          assert(bool(valid), "VALID was withdrawn under backpressure")
          assert(payload == expected, "Payload changed under backpressure")
        }

      while (readDelivered < requests.size || writeDelivered < requests.size) {
        assert(cycle < 8000, "Width conversion did not complete")
        val readRequest  = requests(math.min(readSent, requests.size - 1))
        val writeRequest = requests(math.min(writeSent, requests.size - 1))
        dut.io.sAcorn.rd.cmd.valid.poke((readSent < requests.size).B)
        dut.io.sAcorn.rd.cmd.bits.addr.poke(readRequest.address.U)
        dut.io.sAcorn.wr.cmd.valid.poke((writeSent < requests.size).B)
        dut.io.sAcorn.wr.cmd.bits.addr.poke(writeRequest.address.U)
        dut.io.sAcorn.wr.cmd.bits.data.poke(writeRequest.data.U)
        dut.io.sAcorn.wr.cmd.bits.strobe.poke(writeRequest.strobe.U)
        val readCmdReady  = random.nextBoolean()
        val writeCmdReady = cycle >= 40 && random.nextBoolean()
        dut.io.mAcorn.rd.cmd.ready.poke(readCmdReady.B)
        dut.io.mAcorn.wr.cmd.ready.poke(writeCmdReady.B)
        val readResponseValid  = readResponses.headOption.exists(_.due <= cycle)
        val writeResponseValid = writeResponses.headOption.exists(_.due <= cycle)
        dut.io.mAcorn.rd.rsp.valid.poke(readResponseValid.B)
        dut.io.mAcorn.rd.rsp.bits.data.poke(readResponses.headOption.fold(BigInt(0))(_.data).U)
        dut.io.mAcorn.rd.rsp.bits.error.poke(readResponses.headOption.exists(_.error).B)
        dut.io.mAcorn.wr.rsp.valid.poke(writeResponseValid.B)
        dut.io.mAcorn.wr.rsp.bits.error.poke(writeResponses.headOption.exists(_.error).B)
        val readValid  = bool(dut.io.sAcorn.rd.rsp.valid)
        val writeValid = bool(dut.io.sAcorn.wr.rsp.valid)
        if (readValid) readWait += 1 else readWait = 0
        if (writeValid) writeWait += 1 else writeWait = 0
        val readRspReady  = cycle >= 80 && readWait >= 5 && random.nextBoolean()
        val writeRspReady = cycle >= 130 && writeWait >= 5 && random.nextBoolean()
        dut.io.sAcorn.rd.rsp.ready.poke(readRspReady.B)
        dut.io.sAcorn.wr.rsp.ready.poke(writeRspReady.B)

        val readCmdPayload  = Seq(dut.io.mAcorn.rd.cmd.bits.addr.peek().litValue)
        val writeCmdPayload = Seq(
          dut.io.mAcorn.wr.cmd.bits.addr.peek().litValue,
          dut.io.mAcorn.wr.cmd.bits.data.peek().litValue,
          dut.io.mAcorn.wr.cmd.bits.strobe.peek().litValue,
        )
        val readRspPayload =
          Seq(dut.io.sAcorn.rd.rsp.bits.data.peek().litValue, dut.io.sAcorn.rd.rsp.bits.error.peek().litValue)
        val writeRspPayload = Seq(dut.io.sAcorn.wr.rsp.bits.error.peek().litValue)
        checkHeld(heldReadCmd, dut.io.mAcorn.rd.cmd.valid, readCmdPayload)
        checkHeld(heldWriteCmd, dut.io.mAcorn.wr.cmd.valid, writeCmdPayload)
        checkHeld(heldReadRsp, dut.io.sAcorn.rd.rsp.valid, readRspPayload)
        checkHeld(heldWriteRsp, dut.io.sAcorn.wr.rsp.valid, writeRspPayload)

        val readCmdFire   = bool(dut.io.sAcorn.rd.cmd.valid) && bool(dut.io.sAcorn.rd.cmd.ready)
        val writeCmdFire  = bool(dut.io.sAcorn.wr.cmd.valid) && bool(dut.io.sAcorn.wr.cmd.ready)
        val endpointRead  = bool(dut.io.mAcorn.rd.cmd.valid) && readCmdReady
        val endpointWrite = bool(dut.io.mAcorn.wr.cmd.valid) && writeCmdReady
        val returnedRead  = readResponseValid && bool(dut.io.mAcorn.rd.rsp.ready)
        val returnedWrite = writeResponseValid && bool(dut.io.mAcorn.wr.rsp.ready)

        if (endpointRead) {
          assert(readReceived < expectedReads.size, "Extra read or a read after an earlier error")
          dut.io.mAcorn.rd.cmd.bits.addr.expect(expectedReads(readReceived).address.U)
        }
        if (endpointWrite) {
          assert(writeReceived < expectedWrites.size, "Extra write or a write after an earlier error")
          val expected = expectedWrites(writeReceived)
          dut.io.mAcorn.wr.cmd.bits.addr.expect(expected.address.U)
          dut.io.mAcorn.wr.cmd.bits.data.expect(expected.data.U)
          dut.io.mAcorn.wr.cmd.bits.strobe.expect(expected.strobe.U)
        }
        if (readValid) {
          val error = readParts(readDelivered).isEmpty || readParts(readDelivered).exists(_.error)
          dut.io.sAcorn.rd.rsp.bits.error.expect(error.B)
          dut.io.sAcorn.rd.rsp.bits.data
            .expect((if (error) BigInt(0) else memoryWord(requests(readDelivered).address, input.bytesPerWord)).U)
          dut.io.sAcorn.rd.cmd.ready.expect(false.B)
        }
        if (writeValid) {
          val error = writeParts(writeDelivered).isEmpty || writeParts(writeDelivered).exists(_.error)
          dut.io.sAcorn.wr.rsp.bits.error.expect(error.B)
          dut.io.sAcorn.wr.cmd.ready.expect(false.B)
        }
        heldReadCmd = Option.when(bool(dut.io.mAcorn.rd.cmd.valid) && !readCmdReady)(readCmdPayload)
        heldWriteCmd = Option.when(bool(dut.io.mAcorn.wr.cmd.valid) && !writeCmdReady)(writeCmdPayload)
        heldReadRsp = Option.when(readValid && !readRspReady)(readRspPayload)
        heldWriteRsp = Option.when(writeValid && !writeRspReady)(writeRspPayload)
        dut.clock.step()

        if (readCmdFire) readSent += 1
        if (writeCmdFire) writeSent += 1
        if (returnedRead) {
          readResponses.dequeue()
          capturedRead ||= !readRspReady
        }
        if (returnedWrite) {
          writeResponses.dequeue()
          capturedWrite ||= !writeRspReady
        }
        if (endpointRead) {
          val expected = expectedReads(readReceived)
          readResponses.enqueue(
            Response(cycle + 2 + random.nextInt(4), memoryWord(expected.address, output.bytesPerWord), expected.error)
          )
          readReceived += 1
        }
        if (endpointWrite) {
          writeResponses.enqueue(Response(cycle + 2 + random.nextInt(4), 0, expectedWrites(writeReceived).error))
          writeReceived += 1
        }
        if (readValid && readRspReady) {
          readDelivered += 1
          readBeforeWrite ||= writeDelivered == 0
        }
        if (writeValid && writeRspReady) writeDelivered += 1
        assert(readSent - readDelivered >= 0 && readSent - readDelivered <= 1)
        assert(writeSent - writeDelivered >= 0 && writeSent - writeDelivered <= 1)
        assert(readResponses.size <= 1 && writeResponses.size <= 1)
        cycle += 1
      }
      assert(readReceived == expectedReads.size && writeReceived == expectedWrites.size)
      assert(readResponses.isEmpty && writeResponses.isEmpty)
      assert(expectedReads.isEmpty || capturedRead, "Read responses did not enter the local buffer under backpressure")
      assert(
        expectedWrites.isEmpty || capturedWrite,
        "Write responses did not enter the local buffer under backpressure",
      )
      assert(readBeforeWrite, "Read progress was coupled to the stalled write")
      // Neither a late retry nor a speculative extra subword may appear after completion.
      dut.io.sAcorn.rd.cmd.valid.poke(false.B)
      dut.io.sAcorn.wr.cmd.valid.poke(false.B)
      dut.clock.step(3)
      dut.io.mAcorn.rd.cmd.valid.expect(false.B)
      dut.io.mAcorn.wr.cmd.valid.expect(false.B)
    }
  }

  for ((input, output) <- Seq((8, 64), (32, 64), (64, 32), (256, 32), (64, 8), (32, 32))) {
    s"AcornWidthAdapter($input, $output)" should "convert byte lanes and stop on errors through independent stalls" in {
      runTraffic(input, output)
    }
  }

  "AcornWidthAdapter" should "validate address geometry" in {
    intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(new AcornWidthAdapter(AcornParams(32, 16), AcornParams(64, 15)))
    }
    intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(new AcornWidthAdapter(AcornParams(32, 2), AcornParams(64, 2)))
    }
  }
}
