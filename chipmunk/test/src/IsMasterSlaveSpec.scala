package chipmunk.test

import chipmunk._
import chipmunk.stream._
import chisel3._

class IsMasterSlaveSpec extends ChipmunkFlatSpec {
  "IsMasterSlave" should "help create master/slave bundles" in {
    class MyBundle extends Bundle with IsMasterSlave {
      val a = Input(UInt(3.W))
      val b = Output(UInt(3.W))

      override def isMaster: Boolean = true
    }
    simulate(new Module {
      val io = IO(new Bundle {
        val m = Master(new MyBundle)
        val s = Slave(new MyBundle)
      })
      io.m.b := io.s.b
      io.s.a := io.m.a
    }) { dut =>
      dut.clock.step()
    }
  }

  it should "help create hierarchical master/slave bundles" in {
    class MyBundle extends Bundle with IsMasterSlave {
      val a = Slave(Stream(UInt(3.W)))

      override def isMaster: Boolean = true
    }
    simulate(new Module {
      val io = IO(new Bundle {
        val x = Master(new MyBundle)
        val y = Master(Stream(UInt(3.W)))
      })
      io.y.valid   := io.x.a.valid
      io.x.a.ready := io.y.ready
      io.y.bits    := io.x.a.bits
    }) { dut =>
      dut.clock.step()
    }
  }

  it should "throw Exception when nested-ly decorated" in {
    class MyBundle extends Bundle with IsMasterSlave {
      val a = Input(UInt(3.W))
      val b = Output(UInt(3.W))

      override def isMaster: Boolean = true
    }
    assertThrows[IllegalArgumentException] {
      simulate(new Module {
        val io = IO(new Bundle {
          val m = Master(Master(new MyBundle))
          val s = Slave(new MyBundle)
        })
        io.m <> io.s
      }) { dut =>
        dut.clock.step()
      }
    }
  }
}
