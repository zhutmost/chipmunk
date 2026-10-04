package chipmunk.test.regbank

import chisel3.*

import chipmunk.acorn.AcornIO
import chipmunk.regbank.RegBank
import chipmunk.test.ChipmunkFlatSpec

private[regbank] trait RegBankSpecSupport extends ChipmunkFlatSpec {
  protected def initMaster(port: AcornIO): Unit = {
    port.rd.cmd.valid #= false.B
    port.rd.cmd.bits.addr #= 0.U
    port.rd.rsp.ready #= true.B
    port.wr.cmd.valid #= false.B
    port.wr.cmd.bits.addr #= 0.U
    port.wr.cmd.bits.data #= 0.U
    port.wr.cmd.bits.strobe #= 0.U
    port.wr.rsp.ready #= true.B
  }

  protected def reset(dut: Module): Unit = {
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
  }

  protected def init(dut: RegBank): Unit = {
    initMaster(dut.io.access)
    dut.io.fields.elements.values.foreach(_.backdoorUpdate.foreach { flow =>
      flow.valid #= false.B
      flow.bits #= 0.U
    })
    reset(dut)
  }

  protected def write(dut: RegBank, addr: BigInt, data: BigInt, strobe: BigInt = 3, error: Boolean = false): Unit = {
    val port = dut.io.access.wr
    port.cmd.valid #= true.B
    port.cmd.bits.addr #= addr.U
    port.cmd.bits.data #= data.U
    port.cmd.bits.strobe #= strobe.U
    port.cmd.ready expect true.B
    dut.clock.step()
    port.cmd.valid #= false.B
    port.rsp.valid expect true.B
    port.rsp.bits.error expect error.B
    dut.clock.step()
  }

  protected def read(dut: RegBank, addr: BigInt, data: BigInt, error: Boolean = false): Unit = {
    val port = dut.io.access.rd
    port.cmd.valid #= true.B
    port.cmd.bits.addr #= addr.U
    port.cmd.ready expect true.B
    dut.clock.step()
    port.cmd.valid #= false.B
    port.rsp.valid expect true.B
    port.rsp.bits.data expect data.U
    port.rsp.bits.error expect error.B
    dut.clock.step()
  }
}
