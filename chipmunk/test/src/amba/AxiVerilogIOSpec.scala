package chipmunk.test
package amba

import chipmunk._
import chipmunk.amba._
import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleResource
import chisel3.experimental.dataview._

class NicExample0Bbox extends ExtModule with HasExtModuleResource {
  val clock  = IO(Input(Clock())).suggestName("ACLOCK")
  val resetn = IO(Input(Reset())).suggestName("ARESETN")
  val s0     = FlatIO(
    Slave(
      new Axi4IO(dataWidth = 128, addrWidth = 32, idWidth = 2)
        .createVerilogIO(Seq(PortNameTransform.stringPostfix("_s_axi_spi")))
    )
  )
  val m0 =
    FlatIO(
      Master(
        new Axi4VerilogIO(dataWidth = 128, addrWidth = 32, idWidth = 2)(
          Seq(PortNameTransform.stringPostfix("_m_axi_sram"))
        )
      )
    )

  override def desiredName = "NicExample0"
  addResource("amba/NicExample0.sv")
}

class NicExample1Bbox extends ExtModule with HasExtModuleResource {
  val clock  = IO(Input(Clock()))
  val resetn = IO(Input(Reset()))

  val s0 = IO(Slave(new Axi4VerilogIO(dataWidth = 128, addrWidth = 32, idWidth = 2)())).suggestName("s_axi_spi")
  val m0 = IO(Master(new Axi4VerilogIO(dataWidth = 128, addrWidth = 32, idWidth = 2, hasRegion = true)()))
    .suggestName("m_axi_sram")

  override def desiredName = "NicExample1"
  addResource("amba/NicExample1.sv")
}

class AxiVerilogIOSpec extends ChipmunkFlatSpec {
  "AxiIORtlConnector" should "generate blackbox-friendly AXI interfaces with specific postfix naming" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val s0 = Slave(new Axi4IO(dataWidth = 32, addrWidth = 32, idWidth = 2))
        val m0 = Master(new Axi4IO(dataWidth = 32, addrWidth = 32, idWidth = 2))
      })
      val uNic = Module(new NicExample0Bbox)
      uNic.clock  := clock
      uNic.resetn := !reset.asBool
      val s0View = uNic.s0.viewAs[Axi4IO]
      val m0View = uNic.m0.viewAs[Axi4IO]
      io.s0 <> s0View
      io.m0 <> m0View
    }) { dut =>
      dut.io.s0.aw.bits.id.get.randomize()
      dut.io.s0.aw.bits.addr.randomize()
      dut.io.m0.aw.ready #= true.B
      dut.io.m0.aw.bits.addr expect dut.io.s0.aw.bits.addr.peek()
      dut.io.m0.aw.bits.id.get expect dut.io.s0.aw.bits.id.get.peek()
      dut.io.s0.aw.ready expect dut.io.m0.aw.ready.peek()
    }
  }

  it should "generate blackbox-friendly AXI interfaces with specific prefix naming" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val s0 = Slave(new Axi4IO(dataWidth = 32, addrWidth = 32, idWidth = 2))
        val m0 = Master(new Axi4IO(dataWidth = 32, addrWidth = 32, idWidth = 2, hasRegion = true))
      })
      val uNic = Module(new NicExample1Bbox)
      uNic.clock  := clock
      uNic.resetn := !reset.asBool
      val s0View = uNic.s0.viewAs[Axi4IO]
      val m0View = uNic.m0.viewAs[Axi4IO]
      io.s0 <> s0View
      io.m0 <> m0View
    }) { dut =>
      dut.io.s0.aw.bits.id.get.randomize()
      dut.io.s0.aw.bits.addr.randomize()
      dut.io.m0.aw.ready #= true.B
      dut.io.m0.aw.bits.addr expect dut.io.s0.aw.bits.addr.peek()
      dut.io.m0.aw.bits.id.get expect dut.io.s0.aw.bits.id.get.peek()
      dut.io.s0.aw.ready expect dut.io.m0.aw.ready.peek()
    }
  }
}
