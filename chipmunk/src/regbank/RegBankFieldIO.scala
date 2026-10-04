package chipmunk
package regbank

/** Hardware access by REG_FIELD, or by REG for a single-field register. */
class RegBankFieldIO(regsConfig: Seq[RegElementConfig])
    extends MapBundle[RegFieldBackdoorIO](
      regsConfig
        .flatMap(reg => reg.fields.map(field => s"${reg.name}_${field.name}" -> Master(new RegFieldBackdoorIO(field))))*
    )
    with IsMasterSlave {
  RegBankConfig.validateNames(regsConfig)
  override val isMaster: Boolean = true

  override def apply(key: String): RegFieldBackdoorIO =
    elements.getOrElse(
      key,
      regsConfig
        .collectFirst {
          case reg if reg.name == key && reg.fields.size == 1 => elements(s"${reg.name}_${reg.fields.head.name}")
        }
        .getOrElse(throw new NoSuchElementException(s"Unknown register field: $key")),
    )
}
