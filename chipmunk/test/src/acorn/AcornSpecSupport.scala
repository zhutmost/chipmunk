package chipmunk.test.acorn

import chisel3.*

import chipmunk.acorn.AcornIO
import chipmunk.test.ChipmunkFlatSpec

private[acorn] trait AcornSpecSupport extends ChipmunkFlatSpec {
  protected def reset(dut: Module): Unit = {
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
  }

  protected def initMaster(port: AcornIO): Unit = {
    port.rd.cmd.valid #= false.B
    port.rd.cmd.bits.addr #= 0.U
    port.rd.rsp.ready #= false.B
    port.wr.cmd.valid #= false.B
    port.wr.cmd.bits.addr #= 0.U
    port.wr.cmd.bits.data #= 0.U
    port.wr.cmd.bits.strobe #= 0.U
    port.wr.rsp.ready #= false.B
  }

  protected def initSlave(port: AcornIO): Unit = {
    port.rd.cmd.ready #= false.B
    port.rd.rsp.valid #= false.B
    port.rd.rsp.bits.data #= 0.U
    port.rd.rsp.bits.error #= false.B
    port.wr.cmd.ready #= false.B
    port.wr.rsp.valid #= false.B
    port.wr.rsp.bits.error #= false.B
  }

  protected def bit(value: Bool): Boolean   = value.peek().litToBoolean
  protected def number(value: UInt): BigInt = value.peek().litValue
}
