package chipmunk
package regbank

import chisel3._

/** Access types of a [[RegField]]. By extending this class, you can define your own access type. */
abstract class RegFieldAccessType {

  /** Whether the register field can be read by frontdoor.
    *
    * For an unreadable (write-only) register field, it should be set to true, and the responded data is always 0 during
    * reading. Although it is unreadable, it can still be accessed by backdoor.
    */
  val cannotRead: Boolean = false

  /** The updated value of the register field when writing.
    *
    * @param curr
    *   The current value of the register field.
    * @param wrData
    *   The written value from the frontdoor bus access.
    * @param wrEnable
    *   Whether a valid frontdoor write request is received (i.e., current register element is being written and at
    *   least one corresponding mask bits are non-zero).
    * @return
    *   The data value to be updated to the register field. If it is not None, it will be updated to the field registers
    *   when a frontdoor writing request is received (i.e., `wrEnable === true.B`). Leave it as None if the field is
    *   kept stationary during being written.
    */
  def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = None

  /** The updated value of the register field when reading.
    *
    * @note
    *   This method is to update the register field when a frontdoor read request is received. To implement an
    *   unreadable (write-only) field, you should set [[cannotRead]] to true.
    *
    * @param curr
    *   The current value of the register field.
    * @param rdEnable
    *   Whether a valid frontdoor write request is received (i.e., current register element is being read).
    * @return
    *   The data value to be updated to the register field. If it is not None, it will be updated to the field registers
    *   when a frontdoor read request is received (i.e., `rdEnable === true.B`). Leave it as None if the field is kept
    *   stationary during being read.
    */
  def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] = None
}

/** The 25 register modes pre-defined by UVM. */
object RegFieldAccessType {

  /** RO (Read Only).
    *
    * Write: no effect. Read: No effect.
    */
  object ReadOnly extends RegFieldAccessType

  /** RW (Read, write).
    *
    * Write: changed to written value. Read: no effect.
    */
  object ReadWrite extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(wrData)
  }

  /** RC (Read Clears All).
    *
    * Write: no effect. Read: sets all bits to 0’s.
    */
  object ReadClear extends RegFieldAccessType {
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** RS (Read Sets All).
    *
    * Write: no effect. Read: sets all bits to 1’s.
    */
  object ReadSet extends RegFieldAccessType {
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
  }

  /** WRC (Write, Read Clears All).
    *
    * Write: changed to written value. Read: sets all bits to 0’s.
    */
  object WriteReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** WRS (Write, Read Sets All).
    *
    * Write: changed to written value. Read: sets all bits to 1’s.
    */
  object WriteReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
  }

  /** WC (Write Clears All).
    *
    * Write: sets all bits to 0’s. Read: no effect.
    */
  object WriteClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** WS (Write Sets All).
    *
    * Write: sets all bits to 1’s. Read: no effect.
    */
  object WriteSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
  }

  /** WSRC (Write Sets All, Read Clears All).
    *
    * Write: sets bits to 1’s. Read: sets bits to 0’s.
    */
  object WriteSetReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** WCRS (Write Clears All, Read Sets All).
    *
    * Write: sets bits to 0’s. Read: sets bits to 1’s.
    */
  object WriteClearReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(0.U)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
  }

  /** W1C (Write One to Clear).
    *
    * Write: clear the field bits only if the corresponding written value bits is 1. Read: no effect.
    */
  object WriteOneClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr & (~wrData).asUInt)
  }

  /** W1S (Write One to Set).
    *
    * Write: set the field bits only if the corresponding written value bits is 1. Read: no effect.
    */
  object WriteOneSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr | wrData)
  }

  /** W1T (Write One to Toggle).
    *
    * Write: toggle the field bits only if the corresponding written value bits is 1. Read: no effect.
    */
  object WriteOneToggle extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr ^ wrData)
  }

  /** W0C (Write Zero to Clear).
    *
    * Write: clear the field bits only if the corresponding written value bits is 0. Read: no effect.
    */
  object WriteZeroClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr & wrData)
  }

  /** W0S (Write Zero to Set).
    *
    * Write: toggle the field bits only if the corresponding written value bits is 0. Read: no effect.
    */
  object WriteZeroSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr | (~wrData).asUInt)
  }

  /** W0T (Write Zero to Toggle).
    *
    * Write: toggle the field bits only if the corresponding written value bits is 0. Read: no effect.
    */
  object WriteZeroToggle extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr ^ (~wrData).asUInt)
  }

  /** W1SRC (Write One to Set, Read Clears All).
    *
    * Write: set the field bits only if the corresponding written value bits is 1. Read: sets all bits to 0’s.
    */
  object WriteOneSetReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr | wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** W1CRS (Write One to Clear, Read Sets All).
    *
    * Write: clear the field bits only if the corresponding written value bits is 1. Read: sets all bits to 1’s.
    */
  object WriteOneClearReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr & (~wrData).asUInt)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
  }

  /** W0SRC (Write Zero to Set, Read Clears All).
    *
    * Write: set the field bits only if the corresponding written value bits is 0. Read: sets all bits to 0’s.
    */
  object WriteZeroSetReadClear extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr | (~wrData).asUInt)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** W0CRS (Write Zero to Clear, Read Sets All).
    *
    * Write: clear the field bits only if the corresponding written value bits is 0. Read: sets all bits to 1’s.
    */
  object WriteZeroClearReadSet extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(curr & wrData)
    override def readUpdateData(curr: UInt, rdEnable: Bool): Option[UInt] =
      Some(curr.filledOnes)
  }

  /** WO (Write Only).
    *
    * Write: changed to written value. Read: no effect, and read-back result is 0.
    */
  object WriteOnly extends RegFieldAccessType {
    override val cannotRead: Boolean = true

    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(wrData)
  }

  /** WOC (Write Only Clears All).
    *
    * Write: sets all bits to 0’s. Read: no effect, and read-back result is 0.
    */
  object WriteOnlyClear extends RegFieldAccessType {
    override val cannotRead: Boolean = true

    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] =
      Some(0.U)
  }

  /** WOS (Write Only Sets All).
    *
    * Write: sets all bits to 1’s. Read: no effect, and read-back result is 0.
    */
  object WriteOnlySet extends RegFieldAccessType {
    override val cannotRead: Boolean = true

    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = {
      Some(curr.filledOnes)
    }
  }

  /** W1 (Write Once).
    *
    * Write: changed to written value only when the 1st write after reset. Read: no effect.
    */
  object WriteOnce extends RegFieldAccessType {
    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = {
      val haveWritten = RegInit(0.U(curr.getWidth.W))
      when(wrEnable) {
        haveWritten := true.B
      }
      Some(((~haveWritten).asUInt & wrData) | (haveWritten & curr))
    }
  }

  /** WO1 (Write Only, Once).
    *
    * Write: changed to written value only when the 1st write after reset. Read: no effect, and read-back result is 0.
    */
  object WriteOnlyOnce extends RegFieldAccessType {
    override val cannotRead: Boolean = true

    override def writeUpdateData(curr: UInt, wrData: UInt, wrEnable: Bool): Option[UInt] = {
      val haveWritten = RegInit(0.U(curr.getWidth.W))
      when(wrEnable) {
        haveWritten := true.B
      }
      Some(((~haveWritten).asUInt & wrData) | (haveWritten & curr))
    }
  }
}
