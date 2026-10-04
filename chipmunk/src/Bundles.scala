package chipmunk

import scala.collection.immutable.SeqMap

import chisel3.*
import chisel3.experimental.requireIsChiselType
import chisel3.reflect.DataMirror

/** A Bundle with no payload fields. */
final class EmptyBundle extends Bundle

/** A Record indexed by unique, nonempty keys, preserving field order and type.
  *
  * Fields must be unbound Chisel types. Each is cloned to preserve its directions and allow type templates to be
  * reused.
  */
abstract class MapBundle[T <: Data](fields: (String, T)*) extends Record {
  require(fields.forall(_._1.nonEmpty), "MapBundle keys must be nonempty.")
  require(fields.map(_._1).distinct.size == fields.size, "MapBundle keys must be unique.")

  override val elements: SeqMap[String, T] = SeqMap.from(fields.map { case (name, gen) =>
    requireIsChiselType(gen, s"MapBundle field $name")
    name -> DataMirror.internal.chiselTypeClone(gen)
  })

  def apply(key: String): T = elements(key)
}
