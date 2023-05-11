package chipmunk
package sram

import chisel3.util.log2Ceil
import upickle.default._

import scala.math.ceil

object SramFamily extends Enumeration {
  type SramFamily = Value
  val Ram1rw, Ram2rw, Ram1r1w = Value
}
import sram.SramFamily._

object SramPortPriority extends Enumeration {
  type SramPortPriority = Value
  val High, Low = Value
}
import sram.SramPortPriority._

object ExtSramPortDirection extends Enumeration {
  type ExtSramPortDirection = Value
  val In, Out = Value
}

case class SramPort(name: String, priority: SramPortPriority = High)

object SramPort {
  implicit val rw: Reader[SramPort] = reader[ujson.Value].map[SramPort](json => {
    val portPriority = json.obj.getOrElse("priority", default = ujson.Str("HIGH")).str match {
      case "LOW"  => Low
      case "HIGH" => High
      case _      => throw new Error(s"Port priority should be 'LOW/HIGH' but got ${json("priority")}.")
    }
    SramPort(json("name").str, portPriority)
  })
}

case class SramExtraPort(name: String, direction: ExtSramPortDirection.Value, constant: String = null) {
  direction match {
    case ExtSramPortDirection.In  => require(constant != null, "Constant value should be nonnull for input port.")
    case ExtSramPortDirection.Out => require(constant == null, "Constant value should be null for output port.")
  }
}
object SramExtraPort {
  implicit val rw: Reader[SramExtraPort] = reader[ujson.Value].map[SramExtraPort](json => {
    val direction: ExtSramPortDirection.Value = json("direction").str.toLowerCase() match {
      case "in"  => ExtSramPortDirection.In
      case "out" => ExtSramPortDirection.Out
      case _     => throw new Error(s"Extra port direction should be 'IN/OUT' but got ${json("direction")}.")
    }
    val constant: String = json.obj.getOrElse("constant", default = ujson.Str(null)).str
    SramExtraPort(json("name").str, direction, constant)
  })
}

case class SramPortGroup(
  clock: SramPort,
  enable: SramPort,
  address: SramPort,
  write: SramPort = null,
  input: SramPort = null,
  output: SramPort = null,
  mask: SramPort = null
)

object SramPortGroup {
  implicit val rw: Reader[SramPortGroup] = macroR
}

/** Configuration for SRAM compilation.
  *
  * In most cases, the configuration is created by [[SramCompiler]] according to the user JSON files.
  *
  * @param family
  *   String to specify which type SRAM to be created. Can be one of ["ram1rw", "ram2rw", "ram1r1w"].
  * @param dataWidth
  *   The data width of the generated SRAM.
  * @param depth
  *   The depth (i.e. word count) of the generated SRAM.
  * @param maskUnit
  *   The mask granularity for writing. In general, it should be a power of 2. Keep it 0 to generate no mask signal.
  * @param ports
  *   A List of [[SramPortGroup]] to describe the name & active priority of ports.
  */
case class SramCompilerConfig(
  family: SramFamily,
  nameRule: String,
  maskUnit: Int = 0,
  ports: List[SramPortGroup],
  extraPorts: List[SramExtraPort] = null
) {
  val hasMask: Boolean = maskUnit > 0

  if (!hasMask) {
    require(ports.forall(_.mask == null))
  }

  val portsSize: Int = family match {
    case SramFamily.Ram1rw => 1
    case _                 => 2
  }
  require(ports.length == portsSize, s" 'ports' list length should be $portsSize for $family but got ${ports.length}.")

  if (family == SramFamily.Ram1rw || family == SramFamily.Ram2rw) {
    require(ports.forall { e =>
      e.write != null && e.input != null && e.output != null
    })
  }
  if (family == SramFamily.Ram1r1w) {
    require(ports.forall(_.write == null))
    require(ports(0).input != null)
    require(ports(0).output == null)
    require(ports(1).input == null)
    require(ports(1).mask == null)
    require(ports(1).output != null)
  }

  def genConfig(dataWidth: Int, depth: Int, mux: Int = 1, forceName: String = null): SramConfig = {
    val nameGenerated = nameRule
      .replaceAll("\\$width\\$", s"$dataWidth")
      .replaceAll("\\$depth\\$", s"$depth")
      .replaceAll("\\$mux\\$", s"$mux")
    val name = if (forceName == null) nameGenerated else forceName
    SramConfig(name, dataWidth, depth, mux, this)
  }
}

object SramCompilerConfig {
  implicit val rw: Reader[SramCompilerConfig] = reader[ujson.Value].map[SramCompilerConfig](json => {
    val family: SramFamily = json.obj("family").str.toLowerCase match {
      case "ram1rw"  => SramFamily.Ram1rw
      case "ram1r1w" => SramFamily.Ram1r1w
      case "ram2rw"  => SramFamily.Ram2rw
      case s         => throw new Error(s"Field 'family' should be 'ram1rw/ram2rw/ram1r1w' but got $s.")
    }
    val nameRule: String                = json("nameRule").str
    val maskUnit: Int                   = json.obj.getOrElse("maskUnit", default = ujson.Num(0)).num.toInt
    val ports: List[SramPortGroup]      = read[List[SramPortGroup]](json("ports"))
    val extraPorts: List[SramExtraPort] = read[List[SramExtraPort]](json.obj.getOrElse("extraPorts", ujson.Value("[]")))
    SramCompilerConfig(family, nameRule, maskUnit, ports, extraPorts)
  })
}

object SramCompiler {
  def apply(s: ujson.Readable): SramCompilerConfig = {
    val mc = read[SramCompilerConfig](s)
    mc
  }

  def apply(s: scala.io.BufferedSource): SramCompilerConfig = {
    val mc = SramCompiler(s.mkString)
    mc
  }
}

case class SramConfig(name: String, dataWidth: Int, depth: Int, mux: Int = 1, mc: SramCompilerConfig) {
  require(dataWidth > 0, s"Field 'dataWidth' should be larger than zero but got $dataWidth.")
  require(depth > 0, s"Field 'depth' should be larger than zero but got $depth.")

  val hasMask: Boolean = mc.hasMask
  val maskUnit: Int    = mc.maskUnit

  val addrWidth: Int = log2Ceil(depth)
  val maskWidth: Int = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0
}
