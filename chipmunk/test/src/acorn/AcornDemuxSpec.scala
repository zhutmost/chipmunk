package chipmunk.test.acorn

import chisel3.*

import chipmunk.acorn.*

class AcornDemuxSpec extends AcornSpecSupport {
  "AcornDemux" should "record a held selector and preserve response order across destinations" in {
    simulate(new AcornDemux(AcornParams(32, 12), 3, AcornOutstanding(3, 3))) { dut =>
      initMaster(dut.io.in)
      dut.io.outs.foreach(initSlave)
      dut.io.rdSelect #= 0.U
      dut.io.wrSelect #= 0.U
      reset(dut)
      dut.io.in.rd.cmd.valid #= true.B
      dut.io.in.rd.cmd.bits.addr #= 0x40.U
      dut.clock.step()
      dut.io.rdSelect #= 1.U
      dut.io.outs(0).rd.cmd.ready #= true.B
      dut.io.outs(1).rd.cmd.ready #= true.B
      dut.io.outs(0).rd.cmd.valid expect true.B
      dut.io.outs(1).rd.cmd.valid expect false.B
      dut.clock.step()

      dut.io.in.rd.cmd.bits.addr #= 0x80.U
      dut.io.outs(1).rd.cmd.valid expect true.B
      dut.clock.step()
      dut.io.in.rd.cmd.valid #= false.B
      dut.io.in.rd.rsp.ready #= true.B
      dut.io.outs(1).rd.rsp.valid #= true.B
      dut.io.outs(1).rd.rsp.bits.data #= 0x2222.U
      dut.io.in.rd.rsp.valid expect false.B
      dut.io.outs(1).rd.rsp.ready expect false.B
      dut.clock.step(3)
      dut.io.outs(0).rd.rsp.valid #= true.B
      dut.io.outs(0).rd.rsp.bits.data #= 0x1111.U
      dut.io.in.rd.rsp.bits.data expect 0x1111.U
      dut.clock.step()
      dut.io.outs(0).rd.rsp.valid #= false.B
      dut.io.in.rd.rsp.bits.data expect 0x2222.U
      dut.clock.step()
      dut.io.outs(1).rd.rsp.valid #= false.B

      dut.io.in.rd.cmd.valid #= true.B
      dut.io.rdSelect #= 3.U
      dut.io.in.rd.cmd.ready expect false.B
      dut.io.outs.foreach(_.rd.cmd.valid.expect(false.B))
      dut.clock.step(2)
      dut.io.rdSelect #= 0.U
      dut.io.in.rd.cmd.ready expect true.B
    }
  }
}
