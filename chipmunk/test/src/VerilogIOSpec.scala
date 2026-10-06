package chipmunk.test

import java.util.Locale

import scala.util.Random

import chisel3.*
import chisel3.experimental.Analog
import chisel3.probe.Probe
import chisel3.reflect.DataMirror
import circt.stage.ChiselStage

import chipmunk.*
import chipmunk.amba.*

private object VerilogIOSpecDut {
  def sourceName(name: String): String = "s_axi_" + name.toLowerCase(Locale.ROOT)
  def sinkName(name: String): String   = name + "_out"

  final class AxiHarness(p: AxiParams, flat: Boolean = false, outerFlipped: Boolean = false) extends Module {
    private def ports = new Bundle {
      val sourceBefore = Slave(new AxiIO(p)).createVerilogIO(f = sourceName)
      val sourceAfter  = Slave(new AxiIO(p).createVerilogIO(f = sourceName))
      val sinkBefore   = Master(new AxiIO(p)).createVerilogIO(f = sinkName)
      val sinkAfter    = Master(new AxiIO(p).createVerilogIO(f = sinkName))
    }
    private def gen = if (outerFlipped) Flipped(ports) else ports
    val io          = if (flat) FlatIO(gen) else IO(gen)

    val sourceBefore: AxiIO = io.sourceBefore.viewAsChiselIO
    val sourceAfter: AxiIO  = io.sourceAfter.viewAsChiselIO
    val sinkBefore: AxiIO   = io.sinkBefore.viewAsChiselIO
    val sinkAfter: AxiIO    = io.sinkAfter.viewAsChiselIO
    if (outerFlipped) {
      sourceBefore :<>= sinkAfter
      sourceAfter :<>= sinkBefore
    } else {
      sinkAfter :<>= sourceBefore
      sinkBefore :<>= sourceAfter
    }

    // Exercise original interface methods as well as its preserved EnumType fields.
    require(sourceBefore.aw.bits.protPrivileged.getWidth == 1)
    val burst: AxiBurstType.Type = sinkAfter.aw.bits.burst
    val response: AxiResp.Type   = sourceAfter.b.bits.resp

    val pairs: Seq[(VerilogIO[AxiIO], VerilogIO[AxiIO])] =
      Seq(io.sourceBefore -> io.sinkAfter, io.sourceAfter -> io.sinkBefore)
  }

  final class LiteHarness(p: AxiLiteParams, flat: Boolean = false) extends Module {
    private def ports = new Bundle {
      val sourceBefore = Slave(new AxiLiteIO(p)).createVerilogIO(f = sourceName)
      val sourceAfter  = Slave(new AxiLiteIO(p).createVerilogIO(f = sourceName))
      val sinkBefore   = Master(new AxiLiteIO(p)).createVerilogIO(f = sinkName)
      val sinkAfter    = Master(new AxiLiteIO(p).createVerilogIO(f = sinkName))
    }
    val io                      = if (flat) FlatIO(ports) else IO(ports)
    val sourceBefore: AxiLiteIO = io.sourceBefore.viewAsChiselIO
    val sourceAfter: AxiLiteIO  = io.sourceAfter.viewAsChiselIO
    val sinkBefore: AxiLiteIO   = io.sinkBefore.viewAsChiselIO
    val sinkAfter: AxiLiteIO    = io.sinkAfter.viewAsChiselIO
    sinkAfter :<>= sourceBefore
    sinkBefore :<>= sourceAfter

    require(sinkBefore.ar.bits.protNonsecure.getWidth == 1)
    val response: AxiResp.Type                                   = sinkAfter.r.bits.resp
    val pairs: Seq[(VerilogIO[AxiLiteIO], VerilogIO[AxiLiteIO])] =
      Seq(io.sourceBefore -> io.sinkAfter, io.sourceAfter -> io.sinkBefore)
  }

  final class ExternalLite(p: AxiLiteParams) extends ExtModule {
    val io = FlatIO(Slave(new AxiLiteIO(p).createVerilogIO(f = _ + "_ip")))
  }

  final class ExternalHarness(p: AxiLiteParams) extends Module {
    val io       = FlatIO(Slave(new AxiLiteIO(p).createVerilogIO(f = sourceName)))
    val external = Module(new ExternalLite(p))
    external.io.viewAsChiselIO :<>= io.viewAsChiselIO
  }

  final class ReusedTypeHarness(template: MasterSlaveVerilogIO[AxiLiteIO]) extends Module {
    val source = IO(Slave(template))
    val sink   = IO(Master(new AxiLiteIO(32, 24).createVerilogIO()))
    sink.viewAsChiselIO :<>= source.viewAsChiselIO
  }

  final class IndependentAxiHarness(p: AxiParams) extends Module {
    val source = FlatIO(Slave(new AxiIO(p).createVerilogIO()))
    val sink   = IO(Master(new AxiIO(p)))
    sink :<>= source.viewAsChiselIO
  }

  final class DeclaredSlaveIO extends Bundle with IsMasterSlave with HasVerilogIO {
    override def isMaster: Boolean                           = false
    val request                                              = Input(UInt(8.W))
    val response                                             = Output(UInt(8.W))
    override def generatePortName(path: Seq[String]): String = path.mkString("_").toUpperCase(Locale.ROOT)
  }

  final class DeclaredSlaveHarness(roleBefore: Boolean) extends Module {
    val source = IO(
      if (roleBefore) Slave(new DeclaredSlaveIO).createVerilogIO()
      else Slave(new DeclaredSlaveIO().createVerilogIO())
    )
    val sink = IO(
      if (roleBefore) Master(new DeclaredSlaveIO).createVerilogIO()
      else Master(new DeclaredSlaveIO().createVerilogIO())
    )
    val sourceView = source.viewAsChiselIO
    val sinkView   = sink.viewAsChiselIO
    sinkView.request    := sourceView.request
    sourceView.response := sinkView.response
  }

  final class CoercedAxiHarness(inputs: Boolean, outer: Boolean) extends Module {
    private def ports = new Bundle {
      val axi = new AxiIO(32, 24).createVerilogIO()
    }
    val io =
      if (outer) IO(if (inputs) Input(ports) else Output(ports))
      else
        IO(new Bundle {
          val axi =
            if (inputs) Input(new AxiIO(32, 24).createVerilogIO())
            else Output(new AxiIO(32, 24).createVerilogIO())
        })
    val view: AxiIO = io.axi.viewAsChiselIO
    if (!inputs) view := DontCare
  }

  final class DigitalIO extends Bundle with HasVerilogIO {
    val domainClock                                          = Input(Clock())
    val syncReset                                            = Input(Reset())
    val asyncReset                                           = Input(AsyncReset())
    val signedIn                                             = Input(SInt(9.W))
    val signedOut                                            = Output(SInt(9.W))
    override def generatePortName(path: Seq[String]): String = path.mkString("_")
  }

  final class DigitalHarness extends Module {
    val rtl             = IO(new DigitalIO().createVerilogIO())
    val view: DigitalIO = rtl.viewAsChiselIO
    view.signedOut := view.signedIn
  }

  final class UnsupportedIO(kind: String) extends Bundle with HasVerilogIO {
    val value: Data = kind match {
      case "Analog" => Analog(1.W)
      case "Probe"  => Output(Probe(UInt(8.W)))
      case _        => throw new IllegalArgumentException(s"Unknown field kind: $kind")
    }
    override def generatePortName(path: Seq[String]): String = path.mkString("_")
  }

  final class PlainIO extends Bundle with HasVerilogIO {
    val request = Input(Vec(2, Bool()))
    val result  = Output(Vec(2, UInt(8.W)))
    val status  = Output(AxiResp())

    override def generatePortName(path: Seq[String]): String = path.mkString("_").toUpperCase(Locale.ROOT)
  }

  final class PlainHarness(mode: String) extends Module {
    val rtl: VerilogIO[PlainIO] = mode match {
      case "normal"         => IO(new PlainIO().createVerilogIO())
      case "flipped before" => IO(Flipped(new PlainIO).createVerilogIO())
      case "flipped after"  => IO(Flipped(new PlainIO().createVerilogIO()))
      case "input before"   => IO(Input(new PlainIO).createVerilogIO())
      case "input after"    => IO(Input(new PlainIO().createVerilogIO()))
      case "output before"  => IO(Output(new PlainIO).createVerilogIO())
      case "output after"   => IO(Output(new PlainIO().createVerilogIO()))
      case _                => throw new IllegalArgumentException(s"Unknown plain interface mode: $mode")
    }
    val view: PlainIO = rtl.viewAsChiselIO
    for (field <- view.request if DataMirror.directionOf(field) == ActualDirection.Output) field := false.B
    for (field <- view.result if DataMirror.directionOf(field) == ActualDirection.Output) field  := 0.U
    if (DataMirror.directionOf(view.status) == ActualDirection.Output) view.status               := AxiResp.RESP_OKAY
  }

  final class InvalidHarness(mode: String) extends Module {
    mode match {
      case "bound conversion" => IO(new AxiLiteIO(32, 32)).createVerilogIO()
      case "unbound view"     => new AxiLiteIO(32, 32).createVerilogIO().viewAsChiselIO
      case "repeated role"    => Slave(Slave(new AxiLiteIO(32, 32)).createVerilogIO())
      case _                  => throw new IllegalArgumentException(s"Unknown invalid mode: $mode")
    }
  }
}

class VerilogIOSpec extends ChipmunkFlatSpec {
  import VerilogIOSpecDut.*

  private val masterInputs =
    Set("AWREADY", "WREADY", "BID", "BRESP", "BVALID", "ARREADY", "RID", "RDATA", "RRESP", "RLAST", "RVALID")

  // These are independent expectations for the protocol, not generated from VerilogIO's traversal.
  private def liteWidths(p: AxiLiteParams): Map[String, Int] = Map(
    "AWADDR"  -> p.addrWidth,
    "AWPROT"  -> 3,
    "AWVALID" -> 1,
    "AWREADY" -> 1,
    "WDATA"   -> p.dataWidth,
    "WSTRB"   -> p.strobeWidth,
    "WVALID"  -> 1,
    "WREADY"  -> 1,
    "BRESP"   -> 2,
    "BVALID"  -> 1,
    "BREADY"  -> 1,
    "ARADDR"  -> p.addrWidth,
    "ARPROT"  -> 3,
    "ARVALID" -> 1,
    "ARREADY" -> 1,
    "RDATA"   -> p.dataWidth,
    "RRESP"   -> 2,
    "RVALID"  -> 1,
    "RREADY"  -> 1,
  )

  private def axiWidths(p: AxiParams): Map[String, Int] = {
    val address = Map(
      "ADDR"  -> p.addrWidth,
      "SIZE"  -> 3,
      "LEN"   -> 8,
      "BURST" -> 2,
      "PROT"  -> 3,
      "CACHE" -> 4,
      "LOCK"  -> 1,
      "VALID" -> 1,
      "READY" -> 1,
    )
    val base = Seq("AW", "AR")
      .flatMap(channel =>
        address.map { case (field, width) =>
          (channel + field) -> width
        }
      )
      .toMap ++ Map(
      "WDATA"  -> p.dataWidth,
      "WSTRB"  -> p.strobeWidth,
      "WLAST"  -> 1,
      "WVALID" -> 1,
      "WREADY" -> 1,
      "BRESP"  -> 2,
      "BVALID" -> 1,
      "BREADY" -> 1,
      "RDATA"  -> p.dataWidth,
      "RRESP"  -> 2,
      "RLAST"  -> 1,
      "RVALID" -> 1,
      "RREADY" -> 1,
    )
    base ++
      (if (p.hasId) Seq("AWID", "BID", "ARID", "RID").map(_ -> p.idWidth).toMap else Map.empty) ++
      (if (p.hasQos) Map("AWQOS" -> 4, "ARQOS" -> 4) else Map.empty) ++
      (if (p.hasRegion) Map("AWREGION" -> 4, "ARREGION" -> 4) else Map.empty)
  }

  private def checkPort[T <: Record & HasVerilogIO](
    port: VerilogIO[T],
    master: Boolean,
    widths: Map[String, Int],
    rename: String => String,
  ): Unit = {
    port.elements.keySet shouldBe widths.keySet.map(rename)
    for ((name, width) <- widths) {
      withClue(s"$name: ") {
        val field = port.elements(rename(name))
        field.getWidth shouldBe width
        DataMirror.directionOf(field) shouldBe
          (if (masterInputs.contains(name) == master) ActualDirection.Input else ActualDirection.Output)
      }
    }
  }

  private def checkRtl(rtl: String, widths: Map[String, Int], flat: Boolean): Unit = {
    val prefix = if (flat) "" else "io_"
    val pairs  = Seq("sourceBefore" -> "sinkAfter", "sourceAfter" -> "sinkBefore")
    for ((source, sink) <- pairs; name <- widths.keys) {
      val sourcePort = s"$prefix${source}_${sourceName(name)}"
      val sinkPort   = s"$prefix${sink}_${sinkName(name)}"
      val (to, from) = if (masterInputs.contains(name)) (sourcePort, sinkPort) else (sinkPort, sourcePort)
      withClue(s"$name: ") {
        rtl should include(s"assign $to = $from;")
      }
    }
    rtl should not include "_bits_"
  }

  private def drive(field: Data, value: BigInt): Unit = field match {
    case bool: Bool          => bool #= (value != 0).B
    case uint: UInt          => uint #= value.U(uint.getWidth.W)
    case valueType: EnumType => valueType.poke(value)
    case other               => fail(s"Unexpected AXI field type: ${other.getClass.getSimpleName}")
  }

  private def expectValue(field: Data, value: BigInt): Unit = field match {
    case bool: Bool          => bool expect (value != 0).B
    case uint: UInt          => uint expect value.U(uint.getWidth.W)
    case valueType: EnumType => valueType.expect(value)
    case other               => fail(s"Unexpected AXI field type: ${other.getClass.getSimpleName}")
  }

  private def exercise[T <: Record & HasVerilogIO](
    pairs: Seq[(VerilogIO[T], VerilogIO[T])],
    widths: Map[String, Int],
    clock: Clock,
  ): Unit = {
    val paths = for {
      (pair, pairIndex) <- pairs.zipWithIndex
      name              <- widths.keys.toSeq.sorted
    } yield {
      val (source, sink)  = pair
      val s               = source.elements(sourceName(name))
      val m               = sink.elements(sinkName(name))
      val (input, output) = if (masterInputs.contains(name)) (m, s) else (s, m)
      (s"pair=$pairIndex $name", name, input, output, widths(name))
    }

    // Activate one field at a time to detect cross-channel and cross-interface aliasing.
    paths.foreach { case (_, _, input, _, _) => drive(input, 0) }
    for (selected <- paths.indices) {
      val (_, _, input, _, _) = paths(selected)
      drive(input, 1)
      for (((label, _, _, output, _), index) <- paths.zipWithIndex) {
        withClue(s"selected=$selected $label: ") { expectValue(output, if (index == selected) 1 else 0) }
      }
      drive(input, 0)
    }

    val random = new Random(0x415849L)
    for (_ <- 0 until 16) {
      val values = paths.map { case (_, name, input, _, width) =>
        val value = if (name.endsWith("BURST")) BigInt(random.nextInt(3)) else BigInt(width, random)
        drive(input, value)
        value
      }
      clock.step()
      for (((label, _, _, output, _), value) <- paths.zip(values)) {
        withClue(s"$label: ") { expectValue(output, value) }
      }
    }
  }

  "VerilogIO" should "derive all AXI ports, optional fields, widths, and both role orders" in {
    for {
      idWidth      <- Seq(0, 3)
      qos          <- Seq(false, true)
      region       <- Seq(false, true)
      flat         <- Seq(false, true)
      outerFlipped <- Seq(false, true)
    } {
      val p               = AxiParams(64, 40, idWidth, qos, region)
      var dut: AxiHarness = null
      ChiselStage.emitCHIRRTL { dut = new AxiHarness(p, flat, outerFlipped); dut }
      withClue(s"params=$p flat=$flat outerFlipped=$outerFlipped: ") {
        for ((source, sink) <- dut.pairs) {
          checkPort(source, master = outerFlipped, axiWidths(p), sourceName)
          checkPort(sink, master = !outerFlipped, axiWidths(p), sinkName)
        }
        dut.sourceBefore.params shouldBe p
        dut.sourceAfter.params shouldBe p
        dut.sinkBefore.params shouldBe p
        dut.sinkAfter.params shouldBe p
        dut.burst.getWidth shouldBe 2
        dut.response.getWidth shouldBe 2
      }
    }
  }

  it should "derive precisely the AXI-Lite ports at both supported data widths" in {
    for (dataWidth <- Seq(32, 64); flat <- Seq(false, true)) {
      val p                = AxiLiteParams(dataWidth, 24)
      var dut: LiteHarness = null
      ChiselStage.emitCHIRRTL { dut = new LiteHarness(p, flat); dut }
      for ((source, sink) <- dut.pairs) {
        checkPort(source, master = false, liteWidths(p), sourceName)
        checkPort(sink, master = true, liteWidths(p), sinkName)
      }
      dut.sourceBefore.params shouldBe p
      dut.sinkAfter.params shouldBe p
      dut.response.getWidth shouldBe 2
    }
  }

  for (flat <- Seq(false, true)) {
    it should s"emit the renamed AXI ports and direct view connections with flat=$flat" in {
      val p = AxiParams(64, 40, idWidth = 3, hasQos = true, hasRegion = true)
      checkRtl(ChiselStage.emitSystemVerilog(new AxiHarness(p, flat)), axiWidths(p), flat)
    }

    it should s"emit the renamed AXI-Lite ports and direct view connections with flat=$flat" in {
      val p = AxiLiteParams(32, 24)
      checkRtl(ChiselStage.emitSystemVerilog(new LiteHarness(p, flat)), liteWidths(p), flat)
    }
  }

  it should "transfer every AXI field through views with different names and both role orders" in {
    val p = AxiParams(64, 40, idWidth = 3, hasQos = true, hasRegion = true)
    simulate(new AxiHarness(p)) { dut => exercise(dut.pairs, axiWidths(p), dut.clock) }
  }

  it should "transfer every AXI-Lite field through views with different names and both role orders" in {
    val p = AxiLiteParams(64, 24)
    simulate(new LiteHarness(p)) { dut => exercise(dut.pairs, liteWidths(p), dut.clock) }
  }

  it should "use transformed names directly on ExtModule pins" in {
    val p   = AxiLiteParams(32, 24)
    val rtl = ChiselStage.emitSystemVerilog(new ExternalHarness(p))
    for (name <- liteWidths(p).keys) {
      withClue(s"$name: ") {
        ("\\." + name + "_ip\\s*\\(\\s*" + sourceName(name) + "\\s*\\)").r.findFirstIn(rtl) should not be empty
      }
    }
  }

  it should "clone interface types created outside a Module without sharing leaf instances" in {
    val gen    = new AxiLiteIO(32, 24)
    val first  = gen.createVerilogIO()
    val second = gen.createVerilogIO()
    first.elements.keys.toSeq shouldBe second.elements.keys.toSeq
    for (name <- first.elements.keys) {
      (first.elements(name) eq second.elements(name)) shouldBe false
    }
    DataMirror.specifiedDirectionOf(gen) shouldBe SpecifiedDirection.Unspecified
    gen.isMaster shouldBe true
    ChiselStage.emitSystemVerilog(new ReusedTypeHarness(first)) should include("source_AWADDR")
  }

  it should "support ordinary Bundles and Vecs without requiring IsMasterSlave" in {
    val modes =
      Seq("normal", "flipped before", "flipped after", "input before", "input after", "output before", "output after")
    for (mode <- modes) {
      var dut: PlainHarness = null
      ChiselStage.emitCHIRRTL { dut = new PlainHarness(mode); dut }
      dut.rtl.isInstanceOf[IsMasterSlave] shouldBe false
      dut.rtl.elements.keySet shouldBe Set("REQUEST_0", "REQUEST_1", "RESULT_0", "RESULT_1", "STATUS")
      for ((name, field) <- dut.rtl.elements) {
        val input =
          if (mode.startsWith("input")) true
          else if (mode.startsWith("output")) false
          else name.startsWith("REQUEST") != mode.startsWith("flipped")
        DataMirror.directionOf(field) shouldBe (if (input) ActualDirection.Input else ActualDirection.Output)
      }
      DataMirror.directionOf(dut.view.request(0)) shouldBe DataMirror.directionOf(dut.rtl.elements("REQUEST_0"))
      DataMirror.directionOf(dut.view.result(1)) shouldBe DataMirror.directionOf(dut.rtl.elements("RESULT_1"))
      DataMirror.directionOf(dut.view.status) shouldBe DataMirror.directionOf(dut.rtl.elements("STATUS"))
    }
  }

  it should "reject duplicate names after applying the user transform" in {
    val error = intercept[IllegalArgumentException] { new AxiLiteIO(32, 32).createVerilogIO(f = _ => "same") }
    error.getMessage should include("unique")
  }

  it should "reject empty, malformed, and null field names" in {
    for (name <- Seq("", "1ADDR", "AW-ADDR", "AW.ADDR", "AW ADDR", null)) {
      val error = intercept[IllegalArgumentException] { new AxiLiteIO(32, 32).createVerilogIO(f = _ => name) }
      error.getMessage should include("Invalid RTL field name")
    }
  }

  it should "require an unbound interface when converting to VerilogIO" in {
    intercept[ExpectedChiselTypeException] { ChiselStage.emitCHIRRTL(new InvalidHarness("bound conversion")) }
  }

  it should "require bound hardware when constructing the original interface view" in {
    intercept[ExpectedHardwareException] { ChiselStage.emitCHIRRTL(new InvalidHarness("unbound view")) }
  }

  it should "preserve the guard against applying a role twice across conversion" in {
    val error = intercept[IllegalArgumentException] { ChiselStage.emitCHIRRTL(new InvalidHarness("repeated role")) }
    error.getMessage should include("wrapped twice")
  }

  it should "preserve AXI widths at the minimum and maximum supported data widths" in {
    for (dataWidth <- Seq(8, 1024); addrWidth <- Seq(1, 65)) {
      val p               = AxiParams(dataWidth, addrWidth, idWidth = 1)
      var dut: AxiHarness = null
      ChiselStage.emitCHIRRTL { dut = new AxiHarness(p); dut }
      for ((source, sink) <- dut.pairs) {
        checkPort(source, master = false, axiWidths(p), sourceName)
        checkPort(sink, master = true, axiWidths(p), sinkName)
      }
    }
  }

  it should "connect an independent FlatIO to a hierarchical AXI port using default RTL names" in {
    val p                          = AxiParams(32, 24)
    var dut: IndependentAxiHarness = null
    val rtl                        = ChiselStage.emitSystemVerilog { dut = new IndependentAxiHarness(p); dut }
    checkPort(dut.source, master = false, axiWidths(p), identity)
    rtl should include("assign sink_aw_bits_addr = AWADDR;")
    rtl should include("assign AWREADY = sink_aw_ready;")
    rtl should include("assign RDATA = sink_r_bits_data;")
    rtl should not include "source_AWADDR"
  }

  it should "support interfaces originally declared from the slave perspective" in {
    for (roleBefore <- Seq(false, true)) {
      var dut: DeclaredSlaveHarness = null
      val rtl                       = ChiselStage.emitSystemVerilog { dut = new DeclaredSlaveHarness(roleBefore); dut }
      DataMirror.directionOf(dut.source.elements("REQUEST")) shouldBe ActualDirection.Input
      DataMirror.directionOf(dut.source.elements("RESPONSE")) shouldBe ActualDirection.Output
      DataMirror.directionOf(dut.sink.elements("REQUEST")) shouldBe ActualDirection.Output
      DataMirror.directionOf(dut.sink.elements("RESPONSE")) shouldBe ActualDirection.Input
      rtl should include("assign sink_REQUEST = source_REQUEST;")
      rtl should include("assign source_RESPONSE = sink_RESPONSE;")
    }
  }

  it should "follow Input and Output coercion applied to the flat record or its enclosing Bundle" in {
    for (inputs <- Seq(false, true); outer <- Seq(false, true)) {
      var dut: CoercedAxiHarness = null
      ChiselStage.emitCHIRRTL { dut = new CoercedAxiHarness(inputs, outer); dut }
      val expected = if (inputs) ActualDirection.Input else ActualDirection.Output
      for (field <- dut.io.axi.elements.values) DataMirror.directionOf(field) shouldBe expected
      for (field <- Seq(dut.view.aw.bits.addr, dut.view.aw.ready, dut.view.b.bits.resp, dut.view.r.ready)) {
        DataMirror.directionOf(field) shouldBe expected
      }
    }
  }

  it should "preserve signed, clock, and reset leaf types in the flat record and view" in {
    var dut: DigitalHarness = null
    ChiselStage.emitCHIRRTL { dut = new DigitalHarness; dut }
    dut.rtl.elements("domainClock").isInstanceOf[Clock] shouldBe true
    dut.rtl.elements("syncReset").isInstanceOf[Reset] shouldBe true
    dut.rtl.elements("asyncReset").isInstanceOf[AsyncReset] shouldBe true
    dut.rtl.elements("signedIn").isInstanceOf[SInt] shouldBe true
    dut.rtl.elements("signedOut").getWidth shouldBe 9
    DataMirror.directionOf(dut.view.signedIn) shouldBe ActualDirection.Input
    DataMirror.directionOf(dut.view.signedOut) shouldBe ActualDirection.Output
  }

  for (kind <- Seq("Analog", "Probe")) {
    it should s"reject unsupported $kind fields explicitly" in {
      val error = intercept[IllegalArgumentException] { new UnsupportedIO(kind).createVerilogIO() }
      error.getMessage should include(s"does not support $kind")
    }
  }
}
