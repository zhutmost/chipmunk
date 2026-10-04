package chipmunk.test

import chisel3.*
import chisel3.reflect.DataMirror

import chipmunk.{EmptyBundle, IsMasterSlave, MapBundle, Master, Slave}
import chipmunk.stream.{Stream, StreamIO}

private object BundlesSpecDut {
  class UIntFields(entries: Seq[(String, UInt)]) extends MapBundle[UInt](entries*)

  class Streams(width: Int)
      extends MapBundle[StreamIO[UInt]](
        "forward" -> Master(Stream(UInt(width.W))),
        "reverse" -> Slave(Stream(UInt((width + 1).W))),
      )
      with IsMasterSlave {
    override val isMaster: Boolean = true
  }

  class Harness extends Module {
    val io = IO(new Bundle {
      val in        = Slave(new Streams(8))
      val out       = Master(new Streams(8))
      val valuesIn  = Input(new UIntFields(Seq("small" -> UInt(8.W), "wide" -> UInt(16.W))))
      val valuesOut = Output(new UIntFields(Seq("small" -> UInt(8.W), "wide" -> UInt(16.W))))
      val eventIn   = Slave(Stream(new EmptyBundle))
      val eventOut  = Master(Stream(new EmptyBundle))
    })
    val copies = Wire(Vec(2, chiselTypeOf(io.valuesIn)))
    copies(0)    := io.valuesIn
    copies(1)    := copies(0)
    io.valuesOut := copies(1)
    io.out <> io.in
    io.eventOut << io.eventIn
  }

  class BoundField extends Module {
    val input   = IO(Input(UInt(8.W)))
    val invalid = new UIntFields(Seq("bound" -> input))
  }
}

class BundlesSpec extends ChipmunkFlatSpec {
  "MapBundle" should "preserve field order, types, widths, and directions while cloning templates" in {
    val template = Input(UInt(8.W))
    val fields   = new BundlesSpecDut.UIntFields(Seq("z" -> template, "a" -> template, "wide" -> Output(UInt(16.W))))
    fields.elements.keys.toSeq shouldBe Seq("z", "a", "wide")
    val typed: UInt = fields("wide")
    typed.getWidth shouldBe 16
    fields("z").getWidth shouldBe 8
    (fields("z") eq template) shouldBe false
    (fields("z") eq fields("a")) shouldBe false
    DataMirror.specifiedDirectionOf(fields("z")) shouldBe SpecifiedDirection.Input
    DataMirror.specifiedDirectionOf(fields("wide")) shouldBe SpecifiedDirection.Output
    intercept[NoSuchElementException](fields("missing"))
  }

  it should "allow an empty field map" in {
    new BundlesSpecDut.UIntFields(Seq.empty).elements shouldBe empty
  }

  it should "reject empty or duplicate keys and hardware values" in {
    intercept[IllegalArgumentException](new BundlesSpecDut.UIntFields(Seq("" -> UInt(8.W))))
    intercept[IllegalArgumentException](new BundlesSpecDut.UIntFields(Seq("x" -> UInt(8.W), "x" -> UInt(16.W))))
    intercept[ExpectedChiselTypeException](new BundlesSpecDut.UIntFields(Seq("literal" -> 0.U)))
    intercept[ExpectedChiselTypeException] {
      simulate(new BundlesSpecDut.BoundField) { dut => dut.clock.step() }
    }
  }

  "MapBundle and EmptyBundle" should "support IO flips, aggregate cloning, bulk connections, and empty Stream payloads" in {
    simulate(new BundlesSpecDut.Harness) { dut =>
      dut.io.valuesIn("small") #= 0xa5.U
      dut.io.valuesIn("wide") #= 0xabcd.U
      dut.io.valuesOut("small") expect 0xa5.U
      dut.io.valuesOut("wide") expect 0xabcd.U

      dut.io.in("forward").valid #= true.B
      dut.io.in("forward").bits #= 0x5a.U
      dut.io.out("forward").ready #= false.B
      dut.io.out("forward").valid expect true.B
      dut.io.out("forward").bits expect 0x5a.U
      dut.io.in("forward").ready expect false.B

      dut.io.out("reverse").valid #= true.B
      dut.io.out("reverse").bits #= 0x1ab.U
      dut.io.in("reverse").ready #= true.B
      dut.io.in("reverse").valid expect true.B
      dut.io.in("reverse").bits expect 0x1ab.U
      dut.io.out("reverse").ready expect true.B

      dut.io.eventIn.valid #= true.B
      dut.io.eventOut.ready #= false.B
      dut.io.eventOut.valid expect true.B
      dut.io.eventIn.ready expect false.B
      dut.clock.step()
      dut.io.out("forward").ready #= true.B
      dut.io.in("forward").ready expect true.B
      dut.io.eventOut.ready #= true.B
      dut.io.eventIn.ready expect true.B
      dut.clock.step()
    }
  }
}
