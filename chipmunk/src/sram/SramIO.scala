package chipmunk
package sram

import chisel3._

class SramReadWriteIO(val config: SramConfig) extends Bundle with IsMasterSlave {
  def isMaster = false

  /** Indicates that the current access is valid. Active-High. */
  val enable = Input(Bool())

  /** Indicates that the current access is a write access. Active-High. */
  val write = Input(Bool())

  /** The address to be read/written. */
  val address = Input(UInt(config.addrWidth.W))

  /** Write mask. Active-High (The dataIn slices are written only when its corresponding mask bit is High). */
  val mask = if (config.hasMask) Some(Input(UInt(config.maskWidth.W))) else None

  /** Write data. */
  val dataIn = Input(UInt(config.dataWidth.W))

  /** Readout data. */
  val dataOut = Output(UInt(config.dataWidth.W))

  /** Read/write access to the SRAM.
    *
    * @param enable
    *   Indicates that the current access is valid. Active-High.
    * @param write
    *   Indicates that the current access is a write access. Active-High.
    * @param address
    *   The address to be read/written.
    * @param dataIn
    *   The data to be written.
    * @param mask
    *   Write mask. Active-High (The dataIn slices are written only when its corresponding mask bit is High). Leave it
    *   null if the SRAM does not have mask bits.
    * @return
    *   The readout data.
    */
  def readWrite(enable: Bool, write: Bool, address: UInt, dataIn: UInt, mask: UInt = null): UInt = {
    this.enable  := enable
    this.write   := write
    this.address := address
    this.dataIn  := dataIn
    if (config.hasMask) {
      if (mask != null) {
        this.mask.get := mask
      } else {
        this.mask.get.setAll()
      }
    } else {
      assert(mask == null, "SRAM does not have mask bits.")
    }
    this.dataOut
  }
}

class SramWriteIO(val config: SramConfig) extends Bundle with IsMasterSlave {
  def isMaster = false

  /** Indicates that the current access is valid. Active-High. */
  val enable = Input(Bool())

  /** The address to be written. */
  val address = Input(UInt(config.addrWidth.W))

  /** Write mask. Active-High */
  val mask = if (config.hasMask) Some(Input(UInt(config.maskWidth.W))) else None

  /** Write data. */
  val dataIn = Input(UInt(config.dataWidth.W))

  /** Write access to the SRAM.
    *
    * @param enable
    *   Indicates that the current access is valid. Active-High.
    * @param address
    *   The address to be read/written.
    * @param dataIn
    *   The data to be written.
    * @param mask
    *   Write mask. Active-High (The dataIn slices are written only when its corresponding mask bit is High). Leave it
    *   null if the SRAM does not have mask bits.
    */
  def write(enable: Bool, address: UInt, dataIn: UInt, mask: UInt = null): Unit = {
    if (config.hasMask) {
      if (mask != null) {
        this.mask.get := mask
      } else {
        this.mask.get.setAll()
      }
    } else {
      assert(mask == null, "SRAM does not have mask bits.")
    }
    this.enable  := enable
    this.address := address
    this.dataIn  := dataIn
  }
}

class SramReadIO(val config: SramConfig) extends Bundle with IsMasterSlave {
  def isMaster = false

  /** Indicates that the current access is valid. Active-High. */
  val enable = Input(Bool())

  /** The address to be read. */
  val address = Input(UInt(config.addrWidth.W))

  /** Readout data. */
  val dataOut = Output(UInt(config.dataWidth.W))

  /** Read access to the SRAM.
    *
    * @param enable
    *   Indicates that the current access is valid. Active-High.
    * @param address
    *   The address to be read/written.
    * @return
    *   The readout data.
    */
  def read(enable: Bool, address: UInt): UInt = {
    this.enable  := enable
    this.address := address
    this.dataOut
  }
}
