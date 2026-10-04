package chipmunk
package regbank

import chisel3.*

/** Software access rules. RegField applies the byte mask and collision priority. */
abstract class RegFieldAccessType {

  /** Write-only fields read as zero. Hardware can still access their value. */
  val cannotRead: Boolean = false

  /** Lock software writes after the first accepted write with a nonzero field mask. */
  val writeOnce: Boolean = false

  /** Unmasked next value for a software write; None ignores the write. */
  def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = None

  /** Next value after a software read; None leaves the value unchanged. */
  def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] = None
}

object RegFieldAccessType {

  /** RO: ignore writes. */
  object ReadOnly extends RegFieldAccessType

  /** RW: assign written bits. */
  object ReadWrite extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(wrData)
  }

  /** RC: clear after reading. */
  object ReadClear extends RegFieldAccessType {
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] = Some(0.U)
  }

  /** RS: set all bits after reading. */
  object ReadSet extends RegFieldAccessType {
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] = Some(curr.filledOnes)
  }

  /** WRC: assign on write, clear after read. */
  object WriteReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(0.U)
  }

  /** WRS: assign on write, set after read. */
  object WriteReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(curr.filledOnes)
  }

  /** WC: clear written bits, regardless of write data. */
  object WriteClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(0.U)
  }

  /** WS: set written bits, regardless of write data. */
  object WriteSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr.filledOnes)
  }

  /** WSRC: set written bits, clear after read. */
  object WriteSetReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr.filledOnes)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(0.U)
  }

  /** WCRS: clear written bits, set after read. */
  object WriteClearReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(0.U)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(curr.filledOnes)
  }

  /** W1C: clear bits written as one. */
  object WriteOneClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr & ~wrData)
  }

  /** W1S: set bits written as one. */
  object WriteOneSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr | wrData)
  }

  /** W1T: toggle bits written as one. */
  object WriteOneToggle extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr ^ wrData)
  }

  /** W0C: clear bits written as zero. */
  object WriteZeroClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr & wrData)
  }

  /** W0S: set bits written as zero. */
  object WriteZeroSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr | ~wrData)
  }

  /** W0T: toggle bits written as zero. */
  object WriteZeroToggle extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr ^ ~wrData)
  }

  /** W1SRC: set bits written as one, clear after read. */
  object WriteOneSetReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr | wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(0.U)
  }

  /** W1CRS: clear bits written as one, set after read. */
  object WriteOneClearReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr & ~wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(curr.filledOnes)
  }

  /** W0SRC: set bits written as zero, clear after read. */
  object WriteZeroSetReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr | ~wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(0.U)
  }

  /** W0CRS: clear bits written as zero, set after read. */
  object WriteZeroClearReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr & wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt]                = Some(curr.filledOnes)
  }

  /** WO: assign written bits; reads return zero. */
  object WriteOnly extends RegFieldAccessType {
    override val cannotRead: Boolean                                                     = true
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(wrData)
  }

  /** WOC: clear written bits; reads return zero. */
  object WriteOnlyClear extends RegFieldAccessType {
    override val cannotRead: Boolean                                                     = true
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(0.U)
  }

  /** WOS: set written bits; reads return zero. */
  object WriteOnlySet extends RegFieldAccessType {
    override val cannotRead: Boolean                                                     = true
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(curr.filledOnes)
  }

  /** W1: the first masked software write locks the whole field until reset. */
  object WriteOnce extends RegFieldAccessType {
    override val writeOnce: Boolean                                                      = true
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(wrData)
  }

  /** WO1: write once; reads return zero. */
  object WriteOnlyOnce extends RegFieldAccessType {
    override val cannotRead: Boolean                                                     = true
    override val writeOnce: Boolean                                                      = true
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = Some(wrData)
  }
}
