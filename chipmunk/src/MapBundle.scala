package chipmunk

import chisel3._
import chisel3.experimental.requireIsChiselType
import chisel3.reflect.DataMirror

import scala.collection.immutable.ListMap

/** Base class for a bundle that can be indexed by a string.
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
  val elements = ListMap(elts.map { case (field, elt) =>
    requireIsChiselType(elt)
    field -> DataMirror.internal.chiselTypeClone(elt)
  }: _*)

  def apply(elt: String): T = elements(elt)
}
