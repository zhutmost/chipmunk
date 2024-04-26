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
  * @param postfix
  *   The postfix of the port names. If it is set, the port names will be appended with the postfix (e.g., AWADDR ->
  *   AWADDR_abc). Leave it None if you don't need it.
  * @param toggleCase
  *   Whether to toggle the case of the port names (e.g., AWADDR -> awaddr_abc). Default is false.
  */
class Axi4LiteIORtlConnector(val dataWidth: Int, val addrWidth: Int)(
  postfix: Option[String] = None,
  toggleCase: Boolean = false
) extends RtlConnector(postfix, toggleCase)(
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

object Axi4LiteIORtlConnector {
  implicit val axi4LiteView: DataView[Axi4LiteIORtlConnector, Axi4LiteIO] =
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
