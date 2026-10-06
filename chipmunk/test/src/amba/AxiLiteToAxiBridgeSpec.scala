package chipmunk.test.amba

import scala.util.Random

import chisel3.*
import circt.stage.ChiselStage

import chipmunk.amba.*
import chipmunk.test.ChipmunkFlatSpec

final class AxiLiteToAxiBridgeSpec extends ChipmunkFlatSpec {
  private val responses = Seq(AxiResp.RESP_OKAY, AxiResp.RESP_SLVERR, AxiResp.RESP_DECERR)

  for (
    params <- Seq(AxiParams(32, 16), AxiParams(64, 16, idWidth = 40, hasQos = true, hasRegion = true));
    wFirst <- Seq(true, false)
  ) {
    s"AxiLiteToAxiBridge(${params.dataWidth}, ${params.idWidth}, W first = $wFirst)" should
      "adapt transactions without added latency under independent channel stalls" in {
        val writeId = if (params.hasId) (BigInt(1) << 39) + 3 else BigInt(0)
        val readId  = if (params.hasId) BigInt(17) else BigInt(0)
        simulate(new AxiLiteToAxiBridge(params, writeId, readId)) { dut =>
          val random                              = new Random(42)
          val count                               = 48
          var awSent, wSent, arSent               = 0
          var awReceived, wReceived, arReceived   = 0
          var bProduced, rProduced                = 0
          var bDelivered, rDelivered              = 0
          var cycle                               = 0
          var heldAw, heldAr, heldW, heldB, heldR = Option.empty[Seq[BigInt]]

          def address(index: Int): BigInt = BigInt(index * params.strobeWidth)
          def data(index: Int): BigInt    = (BigInt(1) << (params.dataWidth - 2)) + index
          def strobe(index: Int): BigInt  =
            if (index == count - 1) (BigInt(1) << params.strobeWidth) - 1
            else BigInt(index) & ((BigInt(1) << params.strobeWidth) - 1)
          def response(index: Int): AxiResp.Type                    = responses(index % responses.size)
          def readData(index: Int): BigInt                          = (BigInt(1) << (params.dataWidth - 1)) + index
          def bool(value: Bool): Boolean                            = value.peek().litToBoolean
          def sampleAddress(bits: AxiWriteAddrChannel): Seq[BigInt] =
            Seq(bits.addr, bits.prot, bits.size, bits.len, bits.burst, bits.lock, bits.cache)
              .map(_.peek().litValue) ++
              bits.id.toSeq.map(_.peek().litValue) ++
              bits.qos.toSeq.map(_.peek().litValue) ++
              bits.region.toSeq.map(_.peek().litValue)
          def checkHeld(previous: Option[Seq[BigInt]], valid: Bool, payload: => Seq[BigInt]): Unit =
            previous.foreach { expected =>
              assert(bool(valid), "VALID was withdrawn under backpressure")
              assert(payload == expected, "Payload changed under backpressure")
            }

          while (bDelivered < count || rDelivered < count) {
            assert(cycle < 2000, "Traffic did not complete")
            // Exercise both AW/W arrival orders with different initial channel stalls.
            dut.io.sAxiLite.aw.valid.poke((awSent < count && (!wFirst || cycle >= 5)).B)
            dut.io.sAxiLite.aw.bits.addr.poke(address(awSent).U)
            dut.io.sAxiLite.aw.bits.prot.poke((awSent % 8).U)
            dut.io.sAxiLite.w.valid.poke((wSent < count && (wFirst || cycle >= 5)).B)
            dut.io.sAxiLite.w.bits.data.poke(data(wSent).U)
            dut.io.sAxiLite.w.bits.strb.poke(strobe(wSent).U)
            dut.io.sAxiLite.ar.valid.poke((arSent < count).B)
            dut.io.sAxiLite.ar.bits.addr.poke(address(arSent).U)
            dut.io.sAxiLite.ar.bits.prot.poke((arSent % 8).U)

            val awReady = (!wFirst || cycle >= 20) && random.nextBoolean()
            val wReady  = (wFirst || cycle >= 20) && random.nextBoolean()
            val arReady = random.nextBoolean()
            val bReady  = cycle >= 100 && random.nextBoolean()
            val rReady  = cycle >= 100 && random.nextBoolean()
            dut.io.mAxi.aw.ready.poke(awReady.B)
            dut.io.mAxi.w.ready.poke(wReady.B)
            dut.io.mAxi.ar.ready.poke(arReady.B)
            dut.io.sAxiLite.b.ready.poke(bReady.B)
            dut.io.sAxiLite.r.ready.poke(rReady.B)

            // The target generates exactly one response for each accepted transaction.
            dut.io.mAxi.b.valid.poke((bProduced < math.min(awReceived, wReceived)).B)
            dut.io.mAxi.b.bits.resp.poke(response(bProduced))
            dut.io.mAxi.b.bits.id.foreach(_.poke(writeId.U))
            dut.io.mAxi.r.valid.poke((rProduced < arReceived).B)
            dut.io.mAxi.r.bits.data.poke(readData(rProduced).U)
            dut.io.mAxi.r.bits.resp.poke(response(rProduced))
            dut.io.mAxi.r.bits.last.poke(true.B)
            dut.io.mAxi.r.bits.id.foreach(_.poke(readId.U))

            val awPayload = sampleAddress(dut.io.mAxi.aw.bits)
            val arPayload = sampleAddress(dut.io.mAxi.ar.bits)
            val wPayload  = Seq(
              dut.io.mAxi.w.bits.data.peek().litValue,
              dut.io.mAxi.w.bits.strb.peek().litValue,
              dut.io.mAxi.w.bits.last.peek().litValue,
            )
            val bPayload = Seq(dut.io.sAxiLite.b.bits.resp.peek().litValue)
            val rPayload = Seq(dut.io.sAxiLite.r.bits.data.peek().litValue, dut.io.sAxiLite.r.bits.resp.peek().litValue)
            checkHeld(heldAw, dut.io.mAxi.aw.valid, awPayload)
            checkHeld(heldAr, dut.io.mAxi.ar.valid, arPayload)
            checkHeld(heldW, dut.io.mAxi.w.valid, wPayload)
            checkHeld(heldB, dut.io.sAxiLite.b.valid, bPayload)
            checkHeld(heldR, dut.io.sAxiLite.r.valid, rPayload)

            // Every channel forwards VALID and READY without adding a cycle.
            for (
              (inputValid, outputValid, inputReady, outputReady) <- Seq(
                (dut.io.sAxiLite.aw.valid, dut.io.mAxi.aw.valid, dut.io.sAxiLite.aw.ready, dut.io.mAxi.aw.ready),
                (dut.io.sAxiLite.w.valid, dut.io.mAxi.w.valid, dut.io.sAxiLite.w.ready, dut.io.mAxi.w.ready),
                (dut.io.sAxiLite.ar.valid, dut.io.mAxi.ar.valid, dut.io.sAxiLite.ar.ready, dut.io.mAxi.ar.ready),
                (dut.io.mAxi.b.valid, dut.io.sAxiLite.b.valid, dut.io.mAxi.b.ready, dut.io.sAxiLite.b.ready),
                (dut.io.mAxi.r.valid, dut.io.sAxiLite.r.valid, dut.io.mAxi.r.ready, dut.io.sAxiLite.r.ready),
              )
            ) {
              assert(bool(inputValid) == bool(outputValid), "VALID did not pass through")
              assert(bool(inputReady) == bool(outputReady), "READY did not pass through")
            }

            def checkAddress(bits: AxiWriteAddrChannel, index: Int, id: BigInt): Unit = {
              bits.addr.expect(address(index).U)
              bits.prot.expect((index % 8).U)
              assert(bits.size.peek().litValue == params.fullSize)
              bits.len.expect(0.U)
              bits.burst.expect(AxiBurstType.BURST_INCR)
              bits.lock.expect(0.U)
              bits.cache.expect(0.U)
              bits.id.foreach(_.expect(id.U))
              bits.qos.foreach(_.expect(0.U))
              bits.region.foreach(_.expect(0.U))
            }
            val awFire      = bool(dut.io.mAxi.aw.valid) && awReady
            val wFire       = bool(dut.io.mAxi.w.valid) && wReady
            val arFire      = bool(dut.io.mAxi.ar.valid) && arReady
            val bFire       = bool(dut.io.sAxiLite.b.valid) && bReady
            val rFire       = bool(dut.io.sAxiLite.r.valid) && rReady
            val awInputFire = bool(dut.io.sAxiLite.aw.valid) && bool(dut.io.sAxiLite.aw.ready)
            val wInputFire  = bool(dut.io.sAxiLite.w.valid) && bool(dut.io.sAxiLite.w.ready)
            val arInputFire = bool(dut.io.sAxiLite.ar.valid) && bool(dut.io.sAxiLite.ar.ready)
            val bInputFire  = bool(dut.io.mAxi.b.valid) && bool(dut.io.mAxi.b.ready)
            val rInputFire  = bool(dut.io.mAxi.r.valid) && bool(dut.io.mAxi.r.ready)
            if (awFire) checkAddress(dut.io.mAxi.aw.bits, awReceived, writeId)
            if (arFire) checkAddress(dut.io.mAxi.ar.bits, arReceived, readId)
            if (wFire) {
              dut.io.mAxi.w.bits.data.expect(data(wReceived).U)
              dut.io.mAxi.w.bits.strb.expect(strobe(wReceived).U)
              dut.io.mAxi.w.bits.last.expect(true.B)
            }
            if (bFire) dut.io.sAxiLite.b.bits.resp.expect(response(bDelivered))
            if (rFire) {
              dut.io.sAxiLite.r.bits.data.expect(readData(rDelivered).U)
              dut.io.sAxiLite.r.bits.resp.expect(response(rDelivered))
            }
            heldAw = Option.when(bool(dut.io.mAxi.aw.valid) && !awReady)(awPayload)
            heldAr = Option.when(bool(dut.io.mAxi.ar.valid) && !arReady)(arPayload)
            heldW = Option.when(bool(dut.io.mAxi.w.valid) && !wReady)(wPayload)
            heldB = Option.when(bool(dut.io.sAxiLite.b.valid) && !bReady)(bPayload)
            heldR = Option.when(bool(dut.io.sAxiLite.r.valid) && !rReady)(rPayload)
            dut.clock.step()
            if (awInputFire) awSent += 1
            if (wInputFire) wSent += 1
            if (arInputFire) arSent += 1
            if (awFire) awReceived += 1
            if (wFire) wReceived += 1
            if (arFire) arReceived += 1
            if (bInputFire) bProduced += 1
            if (rInputFire) rProduced += 1
            if (bFire) bDelivered += 1
            if (rFire) rDelivered += 1
            cycle += 1
          }
          assert(
            Seq(awSent, wSent, arSent, awReceived, wReceived, arReceived, bProduced, rProduced, bDelivered, rDelivered)
              .forall(_ == count)
          )
        }
      }
  }

  "AxiLiteToAxiBridge" should "reject unsupported widths and IDs during elaboration" in {
    for (
      (params, writeId, readId) <- Seq(
        (AxiParams(128, 16), BigInt(0), BigInt(0)),
        (AxiParams(32, 16), BigInt(1), BigInt(0)),
        (AxiParams(32, 16, idWidth = 3), BigInt(8), BigInt(0)),
        (AxiParams(32, 16, idWidth = 3), BigInt(0), BigInt(-1)),
      )
    ) {
      intercept[IllegalArgumentException] {
        ChiselStage.emitCHIRRTL(new AxiLiteToAxiBridge(params, writeId, readId))
      }
    }
  }
}
