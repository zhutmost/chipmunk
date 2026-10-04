package chipmunk
package regbank

import scala.collection.immutable.SeqMap

import chisel3.*

/** Hardware access by REG_FIELD, or by REG for a single-field register. */
class RegBankFieldIO(regsConfig: Seq[RegElementConfig]) extends Record with IsMasterSlave {
  RegBankConfig.validateNames(regsConfig)
  override val isMaster: Boolean                            = true
  override val elements: SeqMap[String, RegFieldBackdoorIO] = SeqMap.from(
    regsConfig.flatMap(reg =>
      reg.fields.map(field => s"${reg.name}_${field.name}" -> Master(new RegFieldBackdoorIO(field)))
    )
  )

  def apply(key: String): RegFieldBackdoorIO =
    elements.getOrElse(
      key,
      regsConfig
        .collectFirst {
          case reg if reg.name == key && reg.fields.size == 1 => elements(s"${reg.name}_${reg.fields.head.name}")
        }
        .getOrElse(throw new NoSuchElementException(s"Unknown register field: $key")),
    )
}
