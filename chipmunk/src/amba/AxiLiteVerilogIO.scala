package chipmunk
package amba

import chisel3._
import chisel3.experimental.dataview.DataView

/** Generate a [[Axi4LiteIO]] interface with blackbox-friendly names.
  *
  * @example
  *   {{{
  * val s = FlatIO(Slave(new Axi4LiteIORtlConnector(..., postfix = Some("abc"))))
  * val m = IO(Master(new Axi4LiteIO(...)))
  * val sView = s0.viewAs[Axi4LiteIO]
  * m <> sView
  *   }}}
  *
  * @param dataWidth
  *   The bit width of the bus data.
  * @param addrWidth
  *   The bit width of the bus address.
  * @param portNameTransforms
  *   A sequence of functions to transform the port names. See [[VerilogIO]] for more details.
  */
class Axi4LiteVerilogIO(val dataWidth: Int, val addrWidth: Int)(portNameTransforms: Seq[String => String] = Seq.empty)
    extends VerilogIO(portNameTransforms)(
      "AWADDR"  -> Output(UInt(addrWidth.W)),
      "AWPROT"  -> Output(UInt(3.W)),
      "AWVALID" -> Output(Bool()),
      "AWREADY" -> Input(Bool()),
      "WDATA"   -> Output(UInt(dataWidth.W)),
      "WSTRB"   -> Output(UInt((dataWidth / 8).W)),
      "WVALID"  -> Output(Bool()),
      "WREADY"  -> Input(Bool()),
      "BRESP"   -> Input(AxiResp()),
      "BVALID"  -> Input(Bool()),
      "BREADY"  -> Output(Bool()),
      "ARADDR"  -> Output(UInt(addrWidth.W)),
      "ARPROT"  -> Output(UInt(3.W)),
      "ARVALID" -> Output(Bool()),
      "ARREADY" -> Input(Bool()),
      "RDATA"   -> Input(UInt(dataWidth.W)),
      "RRESP"   -> Input(AxiResp()),
      "RVALID"  -> Input(Bool()),
      "RREADY"  -> Output(Bool())
    )
    with IsMasterSlave {
  override def isMaster = true
}

object Axi4LiteVerilogIO {
  implicit val axi4LiteView: DataView[Axi4LiteVerilogIO, Axi4LiteIO] =
    DataView.mapping(
      rc => new Axi4LiteIO(rc.dataWidth, rc.addrWidth),
      (rc, b) =>
        Seq(
          rc("AWADDR")  -> b.aw.bits.addr,
          rc("AWPROT")  -> b.aw.bits.prot,
          rc("AWVALID") -> b.aw.valid,
          rc("AWREADY") -> b.aw.ready,
          rc("WDATA")   -> b.w.bits.data,
          rc("WSTRB")   -> b.w.bits.strb,
          rc("WVALID")  -> b.w.valid,
          rc("WREADY")  -> b.w.ready,
          rc("BRESP")   -> b.b.bits.resp,
          rc("BVALID")  -> b.b.valid,
          rc("BREADY")  -> b.b.ready,
          rc("ARADDR")  -> b.ar.bits.addr,
          rc("ARPROT")  -> b.ar.bits.prot,
          rc("ARVALID") -> b.ar.valid,
          rc("ARREADY") -> b.ar.ready,
          rc("RDATA")   -> b.r.bits.data,
          rc("RRESP")   -> b.r.bits.resp,
          rc("RVALID")  -> b.r.valid,
          rc("RREADY")  -> b.r.ready
        )
    )
}
