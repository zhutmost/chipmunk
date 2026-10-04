package chipmunk.test.acorn

import chipmunk.acorn.*
import chipmunk.test.ChipmunkFlatSpec

class AcornConfigSpec extends ChipmunkFlatSpec {
  "AcornCrossbarConfig" should "reject ambiguous, unaligned, overflowing, and unknown routes" in {
    val params = AcornParams(32, 16)
    val a      = AcornSlaveConfig("a", 0x1000, 0x100)
    a.portAddrWidth shouldBe 8
    AcornSlaveConfig("a", 0, 0x100, localBase = 0x80).portAddrWidth shouldBe 9
    intercept[IllegalArgumentException](AcornParams(4, 16))
    intercept[IllegalArgumentException](AcornOutstanding(read = 0))
    intercept[IllegalArgumentException](AcornSlaveConfig("a", 0, 0x100, localAddrWidth = Some(7)))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 0, Seq(a)))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 1, Seq(a, a.copy(base = 0x2000))))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 1, Seq(a, a.copy(name = "b", base = 0x1080))))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 1, Seq(a.copy(base = 0x1001))))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 1, Seq(a.copy(base = 0x10000))))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 1, Seq(a.copy(localBase = 1))))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 2, Seq(a), connections = Some(Seq(Set("a")))))
    intercept[IllegalArgumentException](AcornCrossbarConfig(params, 1, Seq(a), connections = Some(Seq(Set("b")))))
    AcornCrossbarConfig(params, 1, Seq(a, a.copy(name = "b", base = 0x1100))).slaves.size shouldBe 2
  }
}
