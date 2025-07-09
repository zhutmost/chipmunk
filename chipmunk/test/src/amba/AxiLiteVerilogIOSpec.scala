package chipmunk.test
package amba

import chipmunk._
import chipmunk.amba._
import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleResource
import chisel3.experimental.dataview._

class NicExample2Bbox(dw: Int = 32, aw: Int = 32)
    extends ExtModule(Map("S00_DW" -> dw, "S00_AW" -> aw, "M00_DW" -> dw, "M00_AW" -> aw))
    with HasExtModuleResource {
  val clock  = IO(Input(Clock()))
  val resetn = IO(Input(Reset()))

  val s0 =
    IO(Slave(new Axi4LiteIO(dataWidth = dw, addrWidth = aw).createVerilogIO(Seq(PortNameTransform.toggleCase))))
      .suggestName("s00_axi")
  val m0 =
    IO(Master(new Axi4LiteIO(dataWidth = dw, addrWidth = aw).createVerilogIO(Seq(PortNameTransform.toggleCase))))
      .suggestName("m00_axi")

  override def desiredName = "NicExample2"
  addResource("amba/NicExample2.sv")
}

class AxiLiteVerilogIOSpec extends ChipmunkFlatSpec {
  "AxiLiteIORtlConnector" should "generate blackbox-friendly AXI-Lite interfaces with specific prefix naming" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val s0 = Slave(new Axi4LiteIO(dataWidth = 32, addrWidth = 32))
        val m0 = Master(new Axi4LiteIO(dataWidth = 32, addrWidth = 32))
      })
      val uNic = Module(new NicExample2Bbox(32, 32))
      uNic.clock  := clock
      uNic.resetn := !reset.asBool
      val s0View = uNic.s0.viewAs[Axi4LiteIO]
      val m0View = uNic.m0.viewAs[Axi4LiteIO]
      io.s0 <> s0View
      io.m0 <> m0View
    }) { dut =>
      dut.io.s0.aw.bits.addr.randomize()
      dut.io.s0.aw.bits.prot.randomize()
      dut.io.m0.aw.ready #= true.B
      dut.io.m0.aw.bits.addr expect dut.io.s0.aw.bits.addr.peek()
      dut.io.s0.aw.ready expect dut.io.m0.aw.ready.peek()
    }
  }
}
