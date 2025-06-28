package chipmunk

import chisel3._
import chisel3.experimental.requireIsChiselType
import chisel3.reflect.DataMirror

import scala.collection.immutable.SeqMap

object PortNameTransform {

  /** Append the port names with a postfix (e.g., AWADDR -> AWADDR_abc). */
  def stringPostfix(s: String): String => String = { pn => pn + s }

  /** Prepend the port names with a prefix (e.g., AwAddr -> i2c_awaddr). */
  def stringPrefix(s: String): String => String = { pn => s + pn }

  /** Convert the port names to lower case (e.g., AwAddr -> awaddr). */
  def lowerCase: String => String = _.toLowerCase

  /** Convert the port names to upper case (e.g., AwAddr -> AWADDR). */
  def upperCase: String => String = _.toUpperCase

  /** Toggle the case of the port names (e.g., AwAddr -> aWaDDR). */
  def toggleCase: String => String =
    _.map {
      case c if c.isUpper => c.toLower
      case c              => c.toUpper
    }

  /** Override the port names with the given map (e.g., Map("AwAddr" -> "i2c_awaddr") will convert AwAddr to
    * i2c_awaddr). If the port name is not in the map, it will remain unchanged.
    */
  def overrideName(overrideNames: Map[String, String]): String => String = { pn =>
    overrideNames.getOrElse(pn, pn)
  }
}

/** A [[MapBundle]]-like bundle for blackbox-friendly interface generation.
  *
  * See [[chipmunk.amba.Axi4VerilogIO]] for an example of usage.
  */
abstract class VerilogIO[+T <: Data](portNameTransforms: Seq[String => String] = Seq.empty)(portMap: (String, T)*)
    extends Record {
  private def formatPortName(portName: String): String = {
    portNameTransforms.foldLeft(portName) { (name, transform) =>
      transform(name)
    }
  }

  val elements = SeqMap(portMap.collect { case (field, elt) =>
    requireIsChiselType(elt)
    formatPortName(field) -> DataMirror.internal.chiselTypeClone(elt)
  }: _*)

  def apply(field: String): T = elements(formatPortName(field))

}

trait WithVerilogIO[T <: VerilogIO[_]] {
  self: Data =>

  /** Create a [[VerilogIO]] with the given port name transforms and port map.
    *
    * @param portNameTransforms
    *   A sequence of functions to transform the port names. We provide some common transforms in [[PortNameTransform]].
    *
    * @see
    *   [[VerilogIO]]
    */
  def createVerilogIO(portNameTransforms: Seq[String => String] = Seq.empty): T
}
