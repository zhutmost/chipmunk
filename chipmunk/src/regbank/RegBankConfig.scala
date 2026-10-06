package chipmunk
package regbank

import chipmunk.acorn.AcornParams

/** Priority of simultaneous software write, read side effect, and hardware update. */
enum RegFieldCollisionMode {
  case WriteReadBackdoor, WriteBackdoorRead, ReadBackdoorWrite
  case ReadWriteBackdoor, BackdoorWriteRead, BackdoorReadWrite
}

/** A field's bit range, reset value, access rule, and optional hardware update port. */
final case class RegFieldConfig(
  name: String,
  baseOffset: Int,
  bitCount: Int,
  initValue: BigInt = 0,
  accessType: RegFieldAccessType = RegFieldAccessType.ReadWrite,
  collisionMode: RegFieldCollisionMode = RegFieldCollisionMode.BackdoorWriteRead,
  backdoorUpdate: Boolean = false,
) {
  require(name.matches("[A-Za-z_][A-Za-z0-9_]*"), s"Invalid register field name: $name")
  require(baseOffset >= 0 && bitCount > 0, s"Field $name needs a nonnegative offset and positive width.")
  require(baseOffset.toLong + bitCount - 1 <= Int.MaxValue, s"Bit range overflows field $name.")
  require(initValue >= 0 && initValue.bitLength <= bitCount, s"Reset value does not fit field $name.")
  val endOffset: Int = (baseOffset.toLong + bitCount - 1).toInt
}

/** One register at a local byte offset. Unused bits read as zero and ignore writes. */
final case class RegElementConfig(name: String, addr: BigInt, fields: Seq[RegFieldConfig]) {
  require(name.matches("[A-Za-z_][A-Za-z0-9_]*"), s"Invalid register name: $name")
  require(addr >= 0, s"Register $name needs a nonnegative byte offset.")
  require(fields.nonEmpty, s"Register $name needs at least one field.")
  require(fields.map(_.name).distinct.size == fields.size, s"Duplicate field name in register $name.")
  require(
    fields.sortBy(_.baseOffset).sliding(2).forall(pair => pair.size < 2 || pair.head.endOffset < pair.last.baseOffset),
    s"Overlapping fields in register $name.",
  )
}

object RegElementConfig {

  /** A register with one field, starting at bit zero. */
  def apply(
    name: String,
    addr: BigInt,
    bitCount: Int,
    initValue: BigInt = 0,
    accessType: RegFieldAccessType = RegFieldAccessType.ReadWrite,
    collisionMode: RegFieldCollisionMode = RegFieldCollisionMode.BackdoorWriteRead,
    backdoorUpdate: Boolean = false,
  ): RegElementConfig =
    RegElementConfig(
      name,
      addr,
      Seq(RegFieldConfig("UNNAMED", 0, bitCount, initValue, accessType, collisionMode, backdoorUpdate)),
    )
}

/** Register map for a naturally aligned, byte-addressed Acorn endpoint. */
final case class RegBankConfig(params: AcornParams, regs: Seq[RegElementConfig]) {
  require(regs.nonEmpty, "RegBank needs at least one register.")
  RegBankConfig.validateNames(regs)
  require(regs.map(_.addr).distinct.size == regs.size, "Register byte offsets must be unique.")
  for (reg <- regs) {
    require(reg.addr.bitLength <= params.addrWidth, s"Byte offset overflows register ${reg.name}.")
    require(reg.addr % params.strobeWidth == 0, s"Register ${reg.name} must be word aligned.")
    require(reg.fields.forall(_.endOffset < params.dataWidth), s"Field exceeds the data width in register ${reg.name}.")
  }
}

object RegBankConfig {
  private[regbank] def validateNames(regs: Seq[RegElementConfig]): Unit = {
    require(regs.map(_.name).distinct.size == regs.size, "Register names must be unique.")
    val keys = regs.flatMap(reg => reg.fields.map(field => s"${reg.name}_${field.name}"))
    require(keys.distinct.size == keys.size, "Register and field names produce duplicate IO keys.")
    val aliases = regs.filter(_.fields.size == 1).map(_.name)
    require(!aliases.exists(keys.contains), "A single-field alias conflicts with a register field IO key.")
  }
}
