package chipmunk.test
package amba

import chipmunk._
import chipmunk.amba._
import chipmunk.tester._
import chisel3._
import chisel3.experimental.ExtModule
import chisel3.experimental.dataview.DataViewable
import chisel3.util.HasExtModuleResource

class NicExample3Bbox(dw: Int = 32, aw: Int = 32)
    extends ExtModule(Map("S00_DW" -> dw, "S00_AW" -> aw, "M00_DW" -> dw, "M00_AW" -> aw))
    with HasExtModuleResource {
  val clock  = IO(Input(Clock()))
  val resetn = IO(Input(Reset()))

  val s0 =
    IO(
      Slave(
        new Apb4IO(dataWidth = dw, addrWidth = aw, hasProt = true, hasStrb = true)
          .createVerilogIO(Seq(PortNameTransform.toggleCase))
      )
    ).suggestName("s00")
  val m0 =
    IO(
      Master(
        new Apb3IO(dataWidth = dw, addrWidth = aw)
          .createVerilogIO(Seq(PortNameTransform.overrideName(Map("PSELX" -> "PSEL")), PortNameTransform.lowerCase))
      )
    ).suggestName("m00")

  override def desiredName = "NicExample3"
  addResource("amba/NicExample3.sv")
}

class ApbVerilogIOSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "ApbIORtlConnector" should "generate blackbox-friendly APB interfaces with specific prefix naming" in {
    compile(new Module {
      val io = IO(new Bundle {
        val s0 = Slave(new Apb4IO(dataWidth = 32, addrWidth = 32, hasProt = true, hasStrb = true))
        val m0 = Master(new Apb3IO(dataWidth = 32, addrWidth = 32))
      })
      val uNic = Module(new NicExample3Bbox(32, 32))
      uNic.clock  := clock
      uNic.resetn := !reset.asBool
      val s0View = uNic.s0.viewAs[Apb4IO]
      val m0View = uNic.m0.viewAs[Apb3IO]
      io.s0 <> s0View
      io.m0 <> m0View
    }).runSim { dut =>
      import TestRunnerUtils._
      dut.io.s0.addr.randomize()
      dut.io.s0.wdata.randomize()
      dut.io.m0.addr expect dut.io.s0.addr.get()
      dut.io.s0.wdata expect dut.io.m0.wdata.get()
    }
  }

}
