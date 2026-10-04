package chipmunk.test.acorn

import chisel3.*

import chipmunk.acorn.*

class AcornMuxSpec extends AcornSpecSupport {
  "AcornMux" should "hold a stalled grant and return outstanding responses to their original masters" in {
    simulate(new AcornMux(AcornParams(32, 12), 2, AcornOutstanding(2, 2))) { dut =>
      dut.io.ins.foreach(initMaster)
      initSlave(dut.io.out)
      reset(dut)

      dut.io.ins(0).rd.cmd.valid #= true.B
      dut.io.ins(0).rd.cmd.bits.addr #= 0x40.U
      dut.io.out.rd.cmd.valid expect true.B
      dut.clock.step()
      dut.io.ins(1).rd.cmd.valid #= true.B
      dut.io.ins(1).rd.cmd.bits.addr #= 0x80.U
      dut.clock.step(2)
      dut.io.out.rd.cmd.bits.addr expect 0x40.U
      dut.io.out.rd.cmd.ready #= true.B
      dut.io.ins(0).rd.cmd.ready expect true.B
      dut.io.ins(1).rd.cmd.ready expect false.B
      dut.clock.step()
      dut.io.ins(0).rd.cmd.valid #= false.B
      dut.io.out.rd.cmd.bits.addr expect 0x80.U
      dut.clock.step()
      dut.io.ins(1).rd.cmd.valid #= false.B

      // The route queue is full; no third request can be accepted.
      dut.io.ins(0).rd.cmd.valid #= true.B
      dut.io.ins(0).rd.cmd.bits.addr #= 0xc0.U
      dut.io.ins(0).rd.cmd.ready expect false.B
      dut.io.out.rd.rsp.valid #= true.B
      dut.io.out.rd.rsp.bits.data #= 0x1234.U
      dut.io.ins(1).rd.rsp.ready #= true.B
      dut.io.ins(0).rd.rsp.valid expect true.B
      dut.io.ins(1).rd.rsp.valid expect false.B
      dut.io.out.rd.rsp.ready expect false.B
      dut.clock.step(3)
      dut.io.ins(0).rd.rsp.bits.data expect 0x1234.U

      dut.io.ins(0).rd.rsp.ready #= true.B
      dut.clock.step()
      dut.io.out.rd.rsp.bits.data #= 0x5678.U
      dut.io.ins(1).rd.rsp.valid expect true.B
      dut.io.ins(1).rd.rsp.bits.data expect 0x5678.U
      dut.io.ins(0).rd.cmd.ready expect true.B
      dut.clock.step()
      dut.io.ins(0).rd.cmd.valid #= false.B
      dut.io.out.rd.rsp.bits.data #= 0x9abc.U
      dut.io.ins(0).rd.rsp.valid expect true.B
      dut.io.ins(0).rd.rsp.bits.data expect 0x9abc.U
      dut.clock.step()
      dut.io.out.rd.rsp.valid #= false.B
      dut.io.ins.foreach(_.rd.rsp.valid.expect(false.B))
    }
  }
}
