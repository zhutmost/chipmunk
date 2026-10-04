package chipmunk.test.regbank

import chipmunk.acorn.AcornParams
import chipmunk.regbank.*
import chipmunk.test.ChipmunkFlatSpec

class RegBankConfigSpec extends ChipmunkFlatSpec {
  private val params = AcornParams(16, 8)
  private val field  = RegFieldConfig("F", 0, 8)
  private val reg    = RegElementConfig("R", 0, Seq(field))

  "RegBankConfig" should "accept reusable Scala descriptions and boundary values" in {
    val high = RegElementConfig("HIGH", 254, Seq(RegFieldConfig("F", 8, 8, initValue = 255)))
    RegBankConfig(params, Seq(reg, high)).regs.last.addr shouldBe 254
    RegBankConfig(params, Seq(reg, high)).regs.last.fields.head.initValue shouldBe 255
    RegBankConfig(AcornParams(32, 16), Seq(reg)).regs shouldBe Seq(reg)
  }

  it should "reject invalid names, bit ranges, and reset values" in {
    intercept[IllegalArgumentException](field.copy(name = "bad-name"))
    intercept[IllegalArgumentException](reg.copy(name = ""))
    intercept[IllegalArgumentException](field.copy(baseOffset = -1))
    intercept[IllegalArgumentException](field.copy(bitCount = 0))
    intercept[IllegalArgumentException](field.copy(baseOffset = Int.MaxValue, bitCount = 2))
    intercept[IllegalArgumentException](field.copy(initValue = -1))
    intercept[IllegalArgumentException](field.copy(initValue = 256))
    intercept[IllegalArgumentException](reg.copy(fields = Seq.empty))
    intercept[IllegalArgumentException](reg.copy(fields = Seq(field, field.copy(baseOffset = 8))))
    intercept[IllegalArgumentException](reg.copy(fields = Seq(field, field.copy(name = "G", baseOffset = 7))))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(reg.copy(fields = Seq(field.copy(bitCount = 17))))))
  }

  it should "reject empty maps, duplicate registers, and invalid byte offsets" in {
    intercept[IllegalArgumentException](RegBankConfig(params, Seq.empty))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(reg, reg.copy(addr = 2))))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(reg, reg.copy(name = "S"))))
    intercept[IllegalArgumentException](reg.copy(addr = -2))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(reg.copy(addr = 1))))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(reg.copy(addr = 256))))
  }

  it should "reject flattened IO key collisions and ambiguous single-field aliases" in {
    val first  = RegElementConfig("A_B", 0, Seq(field.copy(name = "C")))
    val second = RegElementConfig("A", 2, Seq(field.copy(name = "B_C")))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(first, second)))
    val named = RegElementConfig("A", 0, Seq(field.copy(name = "B")))
    val alias = RegElementConfig("A_B", 2, Seq(field))
    intercept[IllegalArgumentException](RegBankConfig(params, Seq(named, alias)))
  }
}
