package chipmunk
package sram

import chisel3._

class SramReadWriteIO(val config: SramConfig) extends Bundle with IsMasterSlave {

  /** Indicates that the current access is valid. Active-High. */
  val enable = Input(Bool())

  /** Indicates that the current access is a write access. Active-High. */
  val write = Input(Bool())

  /** The address to be read/written. */
  val address = Input(UInt(config.addrWidth.W))

  /** Write mask. Active-High */
  val mask = if (config.hasMask) Some(Input(UInt(config.maskWidth.W))) else None

  /** Write data. */
  val dataIn = Input(UInt(config.dataWidth.W))

  /** Readout data. */
  val dataOut = Output(UInt(config.dataWidth.W))

  override def isMaster = false
}

class SramWriteIO(val config: SramConfig) extends Bundle with IsMasterSlave {

  /** Indicates that the current access is valid. Active-High. */
  val enable = Input(Bool())

  /** The address to be written. */
  val address = Input(UInt(config.addrWidth.W))

  /** Write mask. Active-High */
  val mask = if (config.hasMask) Some(Input(UInt(config.maskWidth.W))) else None

  /** Write data. */
  val dataIn = Input(UInt(config.dataWidth.W))

  override def isMaster = false
}

class SramReadIO(val config: SramConfig) extends Bundle with IsMasterSlave {

  /** Indicates that the current access is valid. Active-High. */
  val enable = Input(Bool())

  /** The address to be read. */
  val address = Input(UInt(config.addrWidth.W))

  /** Readout data. */
  val dataOut = Output(UInt(config.dataWidth.W))

  override def isMaster = false
}
