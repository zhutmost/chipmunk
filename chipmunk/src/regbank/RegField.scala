package chipmunk
package regbank

import chisel3.*
import chisel3.util.PriorityMux

import chipmunk.stream.Flow

/** Field value, software access pulses, and an optional hardware assignment port. */
class RegFieldBackdoorIO(fieldConfig: RegFieldConfig) extends Bundle with IsMasterSlave {
  override val isMaster: Boolean = true
  val value                      = Output(UInt(fieldConfig.bitCount.W))

  /** Accepted software write with a nonzero field mask, including writes ignored by the access rule. */
  val isBeingWritten = Output(Bool())

  /** Accepted software read of a readable field. */
  val isBeingRead    = Output(Bool())
  val backdoorUpdate =
    if (fieldConfig.backdoorUpdate) Some(Slave(Flow(UInt(fieldConfig.bitCount.W)))) else None
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

  private val value      = RegInit(config.initValue.U(config.bitCount.W))
  private val written    = if (config.accessType.writeOnce) Some(RegInit(false.B)) else None
  private val wrTransfer = io.frontdoor.wrEnable && io.frontdoor.wrBitMask =/= 0.U
  private val wrAllowed  = wrTransfer && !written.getOrElse(false.B)
  written.foreach { flag => when(wrTransfer) { flag := true.B } }

  private val choices: Map[String, Option[(Bool, UInt)]] = Map(
    "write" -> config.accessType.writeUpdateData(value, io.frontdoor.wrData, wrAllowed).map { data =>
      wrAllowed -> ((data & io.frontdoor.wrBitMask) | (value & ~io.frontdoor.wrBitMask))
    },
    "read" -> config.accessType.readUpdateData(value, io.frontdoor.rdEnable).map(data => io.frontdoor.rdEnable -> data),
    "backdoor" -> io.backdoor.backdoorUpdate.map(flow => flow.fire -> flow.bits),
  )
  private val priority = config.collisionMode match {
    case RegFieldCollisionMode.WriteReadBackdoor => Seq("write", "read", "backdoor")
    case RegFieldCollisionMode.WriteBackdoorRead => Seq("write", "backdoor", "read")
    case RegFieldCollisionMode.ReadBackdoorWrite => Seq("read", "backdoor", "write")
    case RegFieldCollisionMode.ReadWriteBackdoor => Seq("read", "write", "backdoor")
    case RegFieldCollisionMode.BackdoorWriteRead => Seq("backdoor", "write", "read")
    case RegFieldCollisionMode.BackdoorReadWrite => Seq("backdoor", "read", "write")
  }
  value := PriorityMux(priority.flatMap(name => choices(name).toList) :+ (true.B -> value))

  io.backdoor.value          := value
  io.backdoor.isBeingWritten := wrTransfer
  io.backdoor.isBeingRead    := io.frontdoor.rdEnable && !config.accessType.cannotRead.B
  io.frontdoor.rdData        := (if (config.accessType.cannotRead) 0.U else value)
}
