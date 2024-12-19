package chipmunk
package regbank

import stream.Flow

import chisel3._
import chisel3.experimental.requireIsHardware
import chisel3.util._

/** Collision priority of a [[RegField]]. It decides which value should be updated to the register field when multiple
  * update requests happen in a single cycle.
  *
  * There are three update sources: write, read, and backdoor.
  *   - The "write" update source is the write request from the memory-mapped interface. It does not exist when the
  *     write request has no effects on the field value (i.e., the access type is one of ReadOnly, ReadClear, and
  *     ReadSet.)
  *   - The "read" update source is the read request from the memory-mapped interface. It does not exist when the read
  *     access type has no effects on the field value (i.e., the access type is one of ReadOnly, ReadWrite, WriteOnly,
  *     WriteSet, WriteOnlySet, WriteZeroSet, WriteClear, WriteOnlyClear, WriteOneClear, WriteOneSet, WriteZeroClear,
  *     WriteOneToggle, and WriteZeroToggle).
  *   - The "backdoor" update source is the backdoor update request from the backdoor interface. It exists only when the
  *     field configuration allows backdoor update (i.e., `backdoorUpdate` is true).
  *
  * The collision priority is decided by the collision mode. There are six collision modes:
  *   - [[WriteReadBackdoor]]: write > read > backdoor
  *   - [[WriteBackdoorRead]]: write > backdoor > read
  *   - [[ReadBackdoorWrite]]: read > backdoor > write
  *   - [[ReadWriteBackdoor]]: read > write > backdoor
  *   - [[BackdoorWriteRead]]: backdoor > write > read
  *   - [[BackdoorReadWrite]]: backdoor > read > write
  */
object RegFieldCollisionMode extends Enumeration {
  val WriteReadBackdoor, WriteBackdoorRead, ReadBackdoorWrite, ReadWriteBackdoor, BackdoorWriteRead, BackdoorReadWrite =
    Value
}

/** Configuration of a register field used in [[RegElementConfig]].
  *
  * @param name
  *   Name of the register field. It should be unique in a [[RegElementConfig]]. Only alphanumeric and underscore are
  *   allowed.
  * @param baseOffset
  *   Base offset of the register field. The least significant bit of the register field is at this offset.
  * @param bitCount
  *   Bit width of the register field.
  * @param initValue
  *   Initial value of the register field. Default is 0.
  * @param accessType
  *   Access type of the register field. Default is [[RegFieldAccessType.ReadWrite]].
  * @param collisionMode
  *   Collision priority of the register field. Default is [[RegFieldCollisionMode.BackdoorWriteRead]].
  * @param backdoorUpdate
  *   Whether the register field can be updated through backdoor. Default is false.
  */
case class RegFieldConfig(
  name: String,
  baseOffset: Int,
  bitCount: Int,
  initValue: UInt = 0.U,
  accessType: RegFieldAccessType = RegFieldAccessType.ReadWrite,
  collisionMode: RegFieldCollisionMode.Value = RegFieldCollisionMode.BackdoorWriteRead,
  backdoorUpdate: Boolean = false
) {
  require(name matches "[\\w]+", s"Register field name `$name` is invalid")
  require(bitCount > 0, s"Register field should have at least 1 bit, but got $bitCount")
  require(baseOffset >= 0, s"Base offset of field should be >= 0, but got $baseOffset")

  requireIsHardware(initValue, "RegFieldConfig.initValue")

  val endOffset = baseOffset + bitCount - 1
}

private[regbank] class RegFieldBackdoorIO(fieldConfig: RegFieldConfig) extends Bundle with IsMasterSlave {
  val value = Output(UInt(fieldConfig.bitCount.W))

  val isBeingWritten = Output(Bool())
  val isBeingRead    = Output(Bool())

  val backdoorUpdate =
    if (fieldConfig.backdoorUpdate)
      Some(Slave(Flow(UInt(fieldConfig.bitCount.W))))
    else None

  def isMaster = true
}

private[regbank] class RegField(val config: RegFieldConfig) extends Module {
  val io = IO(new Bundle {
    val frontdoor = new Bundle {
      val wrEnable  = Input(Bool())
      val rdEnable  = Input(Bool())
      val wrData    = Input(UInt(config.bitCount.W))
      val wrBitMask = Input(UInt(config.bitCount.W))
      val rdData    = Output(UInt(config.bitCount.W))
    }
    val backdoor = Master(new RegFieldBackdoorIO(config))
  })
  val r = RegInit(UInt(config.bitCount.W), config.initValue)

  val wrEnableValid: Bool = io.frontdoor.wrEnable && io.frontdoor.wrBitMask =/= 0.U

  val writeUpdateDataOption: Option[UInt] =
    config.accessType.writeUpdateData(r, io.frontdoor.wrData, wrEnableValid)
  val readUpdateDataOption: Option[UInt] =
    config.accessType.readUpdateData(r, io.frontdoor.rdEnable)

  val dataNextChoices: Map[String, Option[(Bool, UInt)]] = Map(
    "write" -> writeUpdateDataOption.map { data =>
      io.frontdoor.wrEnable -> {
        (data & io.frontdoor.wrBitMask) | (r & (~io.frontdoor.wrBitMask).asUInt)
      }
    },
    "read"     -> readUpdateDataOption.map { data => io.frontdoor.rdEnable -> data },
    "backdoor" -> io.backdoor.backdoorUpdate.map { flow => flow.fire -> flow.bits },
    "default"  -> Some(true.B -> r)
  )

  val dataNextChoicesPriority = config.collisionMode match {
    case RegFieldCollisionMode.WriteReadBackdoor => Seq("write", "read", "backdoor", "default")
    case RegFieldCollisionMode.WriteBackdoorRead => Seq("write", "backdoor", "read", "default")
    case RegFieldCollisionMode.ReadBackdoorWrite => Seq("read", "backdoor", "write", "default")
    case RegFieldCollisionMode.ReadWriteBackdoor => Seq("read", "write", "backdoor", "default")
    case RegFieldCollisionMode.BackdoorWriteRead => Seq("backdoor", "write", "read", "default")
    case RegFieldCollisionMode.BackdoorReadWrite => Seq("backdoor", "read", "write", "default")
    case _ => throw new IllegalArgumentException("Invalid collision mode")
  }

  r := PriorityMux(dataNextChoicesPriority.flatMap(dataNextChoices(_).toList))

  io.backdoor.value := r

  io.backdoor.isBeingRead    := io.frontdoor.rdEnable && !config.accessType.cannotRead.B
  io.backdoor.isBeingWritten := wrEnableValid

  io.frontdoor.rdData := (if (config.accessType.cannotRead) 0.U else r.asUInt)
}
