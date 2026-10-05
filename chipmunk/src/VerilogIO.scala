package chipmunk

import scala.collection.immutable.SeqMap

import chisel3.*
import chisel3.experimental.{requireIsChiselType, requireIsHardware, SourceInfo}
import chisel3.experimental.dataview.*
import chisel3.reflect.DataMirror

/** Supplies RTL names for leaf paths relative to this interface. */
trait HasVerilogIO {
  this: Record =>

  def generatePortName(path: Seq[String]): String
}

extension [T <: Record & HasVerilogIO](gen: T) {

  /** Converts an unbound interface type to a flat RTL type. */
  def createVerilogIO(f: String => String = identity)(using factory: VerilogIOFactory[T]): factory.Out =
    factory.create(gen, f)
}

/** A flat interface type whose fields and view are derived from one original interface.
  *
  * Bind with IO or FlatIO before calling viewAsChiselIO. Supports digital leaves in Records and Vecs.
  */
class VerilogIO[T <: Record & HasVerilogIO] private[chipmunk] (private val gen: T, private val rename: String => String)
    extends Record {
  requireIsChiselType(gen, "VerilogIO interface")

  private val template = DataMirror.internal.chiselTypeClone(gen)
  private val fields   = VerilogIO.leaves(template)
  private val names    = fields.map(field => rename(template.generatePortName(field.path)))

  require(fields.map(_.gen).distinct.size == fields.size, "VerilogIO does not support aliased leaf fields.")
  names.foreach { name =>
    require(name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*"), s"Invalid RTL field name: $name")
  }
  require(names.distinct.size == names.size, "RTL field names must be unique after renaming.")

  override val elements: SeqMap[String, Data] = SeqMap.from(fields.zip(names).map { case (field, name) =>
    name -> (if (field.isInput) Input(field.gen) else Output(field.gen))
  })

  /** Returns the original interface API over bound RTL fields, without adding hardware. */
  def viewAsChiselIO(using SourceInfo): T = {
    requireIsHardware(this, "VerilogIO view target")

    given DataView[VerilogIO[T], T] = DataView.mapping[VerilogIO[T], T](
      rtl => {
        val view         = DataMirror.internal.chiselTypeClone(template)
        val actualInputs = names.map { name =>
          DataMirror.directionOf(rtl.elements(name)) match {
            case ActualDirection.Input  => true
            case ActualDirection.Output => false
            case other => throw new IllegalArgumentException(s"Unsupported RTL field direction: $other")
          }
        }
        val originalInputs = fields.map(_.isInput)
        // Follow flips and coercion applied after flattening, including enclosing Bundles.
        DataMirror.specifiedDirectionOf(rtl) match {
          case SpecifiedDirection.Input                    => Input(view)
          case SpecifiedDirection.Output                   => Output(view)
          case _ if actualInputs == originalInputs         => view
          case _ if actualInputs == originalInputs.map(!_) => Flipped(view)
          case _ if actualInputs.forall(identity)          => Input(view)
          case _ if actualInputs.forall(!_)                => Output(view)
          case _ => throw new IllegalArgumentException("RTL field directions no longer match the original interface.")
        }
      },
      (rtl, view) => {
        val viewFields = VerilogIO.leaves(view).map(field => field.path -> field.gen).toMap
        require(viewFields.keySet == fields.map(_.path).toSet, "Interface changed during cloning.")
        fields.zip(names).map { case (field, name) => rtl.elements(name) -> viewFields(field.path) }
      },
    )

    this.viewAs[T]
  }
}

private[chipmunk] object VerilogIO {
  private[chipmunk] final case class Leaf(path: Vector[String], gen: Element, isInput: Boolean)

  private[chipmunk] def leaves(
    data: Data,
    path: Vector[String] = Vector.empty,
    parentDirection: SpecifiedDirection = SpecifiedDirection.Unspecified,
  ): Vector[Leaf] = {
    val location = if (path.isEmpty) "<root>" else path.mkString(".")
    require(!DataMirror.hasProbeTypeModifier(data), s"VerilogIO does not support Probe at $location.")
    val direction = SpecifiedDirection.fromParent(parentDirection, DataMirror.specifiedDirectionOf(data))

    data match {
      case record: Record =>
        record.elements.toVector.reverse.flatMap { case (name, field) =>
          require(name.nonEmpty, s"VerilogIO does not support opaque records at $location.")
          leaves(field, path :+ name, direction)
        }

      case vector: Vec[?] =>
        vector.zipWithIndex.toVector.flatMap { case (field, index) =>
          leaves(field, path :+ index.toString, direction)
        }

      case leaf: (Bits | EnumType | Clock | Reset) =>
        val isInput = direction == SpecifiedDirection.Input || direction == SpecifiedDirection.Flip
        Vector(Leaf(path, leaf, isInput))

      case other =>
        throw new IllegalArgumentException(s"VerilogIO does not support ${other.getClass.getSimpleName} at $location.")
    }
  }
}
