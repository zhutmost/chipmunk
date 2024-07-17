package chipmunk
package amba

import chisel3._
import chisel3.experimental.dataview.DataView

class ApbIORtlConnector(
  val dataWidth: Int,
  val addrWidth: Int,
  val hasProt: Boolean = false,
  val hasStrb: Boolean = false
)(postfix: Option[String] = None, toggleCase: Boolean = false, overrideNames: Map[String, String] = Map.empty)
    extends RtlConnector(postfix, toggleCase, overrideNames)({
      val strobeWidth: Int = dataWidth / 8
      val axiPorts = Seq(
        "PADDR"   -> Output(UInt(addrWidth.W)),
        "PSELX"   -> Output(UInt(4.W)),
        "PENABLE" -> Output(Bool()),
        "PWRITE"  -> Output(Bool()),
        "PWDATA"  -> Output(UInt(dataWidth.W)),
        "PREADY"  -> Input(Bool()),
        "PRDATA"  -> Input(UInt(dataWidth.W)),
        "PSLVERR" -> Input(Bool()),
        "PPROT"   -> (if (hasProt) Some(Output(UInt(3.W))) else None),
        "PSTRB"   -> (if (hasStrb) Some(Output(UInt(strobeWidth.W))) else None)
      ).collect {
        case (key, Some(value: Data)) => key -> value
        case (key, value: Data)       => key -> value
      }
      axiPorts
    }: _*)
    with IsMasterSlave {
  override def isMaster = true
}

object ApbIORtlConnector {
  implicit val apb3View: DataView[ApbIORtlConnector, Apb3IO] =
    DataView.mapping(rc => new Apb3IO(rc.dataWidth, rc.addrWidth), (rc, b) => mapPortsToRtlConnector(rc, b))

  implicit val apb4View: DataView[ApbIORtlConnector, Apb4IO] =
    DataView.mapping(
      rc => new Apb4IO(rc.dataWidth, rc.addrWidth, rc.hasProt, rc.hasStrb),
      (rc, b) => mapPortsToRtlConnector(rc, b)
    )

  private def mapPortsToRtlConnector(rc: ApbIORtlConnector, b: ApbIOBase) = {
    var portPairs = Seq(
      rc("PADDR")   -> b.addr,
      rc("PSELX")   -> b.selx,
      rc("PENABLE") -> b.enable,
      rc("PWRITE")  -> b.write,
      rc("PWDATA")  -> b.wdata,
      rc("PREADY")  -> b.ready,
      rc("PRDATA")  -> b.rdata,
      rc("PSLVERR") -> b.slverr
    )
    if (rc.hasProt) {
      portPairs ++= Seq(rc("PPROT") -> b.prot.get)
    }
    if (rc.hasStrb) {
      portPairs ++= Seq(rc("PSTRB") -> b.strb.get)
    }
    portPairs
  }
}
