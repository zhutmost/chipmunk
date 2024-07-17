package chipmunk

import chisel3._
import chisel3.experimental.requireIsChiselType
import chisel3.reflect.DataMirror

import scala.collection.immutable.SeqMap

/** A [[MapBundle]]-like bundle for blackbox-friendly interface generation.
  *
  * See [[chipmunk.amba.Axi4IORtlConnector]] for an example of usage.
  *
  * @param postfix
  *   The postfix of the port names. If it is set, the port names will be appended with the postfix (e.g., AWADDR -> * *
  *   AWADDR_abc). Leave it None if you don't need it.
  * @param toggleCase
  *   Whether to toggle the case of the port names (e.g., AWADDR -> awaddr_abc). Default is false.
  */
abstract class RtlConnector[+T <: Data](
  postfix: Option[String] = None,
  toggleCase: Boolean = false,
  overrideNames: Map[String, String] = Map.empty
)(portMap: (String, T)*)
    extends Record {
  private def toggleCasePortName(portName: String): String = {
    if (toggleCase) portName.map(c => if (c.isUpper) c.toLower else c.toUpper) else portName
  }

  private def postfixPortName(portName: String): String = {
    postfix.map(p => portName + "_" + p).getOrElse(portName)
  }

  private def overridePortName(portName: String): String = {
    overrideNames.getOrElse(portName, portName)
  }

  // Override this method to customize the port name formatting.
  def formatPortName(portName: String): String = {
    postfixPortName(toggleCasePortName(overridePortName(portName)))
  }

  val elements = SeqMap(portMap.collect { case (field, elt) =>
    requireIsChiselType(elt)
    formatPortName(field) -> DataMirror.internal.chiselTypeClone(elt)
  }: _*)

  def apply(field: String): T = elements(formatPortName(field))
}
