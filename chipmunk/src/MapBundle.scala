package chipmunk

import chisel3._
import chisel3.experimental.requireIsChiselType
import chisel3.reflect.DataMirror

import scala.collection.immutable.SeqMap

/** Base class of a bundle that can be indexed by a string.
  *
  * To create a named [[MapBundle]], extend this class and pass in pairs of keys and [[Data]] subtypes. The [[Data]]
  * signals can then be accessed with the string keys.
  *
  * @note
  *   Extending this class as a anonymous class may result in compilation error.
  *
  * @example
  *   {{{
  * class MyBundle extends MapBundle[UInt](
  *   "foo" -> UInt(8.W),
  *   "bar" -> UInt(16.W),
  * )
  * val myBundle = Wire(new MyBundle)
  * val foo = myBundle("foo")
  * myBundle("bar") := 3.U
  *   }}}
  */
abstract class MapBundle[T <: Data](elts: (String, T)*) extends Record {
  val elements = SeqMap(elts.map { case (field, elt) =>
    requireIsChiselType(elt)
    field -> DataMirror.internal.chiselTypeClone(elt)
  }: _*)

  def apply(key: String): T = elements(key)
}

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
abstract class RtlConnector[T <: Data](postfix: Option[String] = None, toggleCase: Boolean = false)(
  portMap: (String, T)*
) extends Record {
  private def toggleCasePortNmae(portName: String): String = {
    if (toggleCase) portName.map(c => if (c.isUpper) c.toLower else c.toUpper) else portName
  }

  private def postfixPortName(portName: String): String = {
    postfix.map(p => portName + "_" + p).getOrElse(portName)
  }

  // Override this method to customize the port name formatting.
  def formatPortName(portName: String): String = {
    postfixPortName(toggleCasePortNmae(portName))
  }

  val elements = SeqMap(portMap.collect { case (field, elt) =>
    requireIsChiselType(elt)
    formatPortName(field) -> DataMirror.internal.chiselTypeClone(elt)
  }: _*)

  def apply(field: String): T = elements(formatPortName(field))
}
