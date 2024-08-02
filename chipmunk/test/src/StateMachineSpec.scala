package chipmunk.test

import chipmunk._
import chipmunk.tester._
import chisel3._

class StateMachineSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "StateMachine" should "work in a elegant style" in {
    val compiled = compile(new Module {
      val io = IO(new Bundle {
        val a           = Input(Bool())
        val b           = Input(Bool())
        val c           = Input(Bool())
        val state       = Output(UInt(2.W))
        val cIsActive   = Output(Bool())
        val bIsEntering = Output(Bool())
        val cIsExiting  = Output(Bool())
      })
      val fsm = new StateMachine {
        val sA = new State with EntryPoint
        val sB = new State
        val sC = new State
        sA
          .whenIsActive {
            when(io.a) {
              goto(sB)
            }
          }
        sB
          .whenIsActive {
            when(io.b) {
              goto(sC)
            }
          }
        sC
          .whenIsActive {
            when(io.c) {
              goto(sA)
            }
          }
      }
      io.state       := fsm.stateCurr
      io.cIsActive   := fsm.isActive(fsm.sC)
      io.bIsEntering := fsm.isEntering(fsm.sB)
      io.cIsExiting  := fsm.isExiting(fsm.sC)
    })
    compiled.runSim { dut =>
      import TestRunnerUtils._
      // a     _ 1 1 1 _ _ _
      // b     _ _ _ 1 _ _ _
      // c     _ _ _ _ _ 1 _
      // state A A B B C C A
      dut.reset #= true.B
      dut.io.a #= false.B
      dut.io.b #= false.B
      dut.io.c #= false.B
      dut.clock.step(5)
      dut.reset #= false.B
      dut.clock.step()
      dut.io.state expect 1.U
      dut.io.a #= true.B
      dut.io.bIsEntering expect true.B
      dut.clock.step(2)
      dut.io.state expect 2.U
      dut.io.b #= true.B
      dut.clock.step()
      dut.io.state expect 3.U
      dut.io.a #= false.B
      dut.io.b #= false.B
      dut.clock.step()
      dut.io.state expect 3.U
      dut.io.cIsActive expect true.B
      dut.io.c #= true.B
      dut.io.cIsExiting expect true.B
      dut.clock.step()
      dut.io.state expect 1.U
      dut.io.c #= false.B
    }
  }

  protected class StateMachineWithEncoding(encoding: StateMachineEncoding) extends Module {
    val io = IO(new Bundle {
      val state = Output(UInt())
    })
    val fsm = new StateMachine(defaultEncoding = encoding) {
      val sA = new State with EntryPoint
      val sB = new State
      val sC = new State
      sA.whenIsActive {
        goto(sB)
      }
      sB.whenIsActive {
        goto(sC)
      }
      sC.whenIsActive {
        goto(sA)
      }
    }
    io.state := fsm.stateCurr
  }

  it should "allow encode FSM states in a binary-sequential encoding" in {
    val compiled = compile(new StateMachineWithEncoding(Sequential))
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true.B
      dut.clock.step(5)
      dut.reset #= false.B
      dut.io.state expect 0.U
      dut.clock.step()
      dut.io.state expect 1.U
      dut.clock.step()
      dut.io.state expect 2.U
      dut.clock.step()
      dut.io.state expect 3.U
    }
  }

  it should "allow encode FSM states in a binary-onehot encoding" in {
    val compiled = compile(new StateMachineWithEncoding(OneHot))
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true.B
      dut.clock.step(5)
      dut.reset #= false.B
      dut.io.state expect "b0001".U
      dut.clock.step()
      dut.io.state expect "b0010".U
      dut.clock.step()
      dut.io.state expect "b0100".U
      dut.clock.step()
      dut.io.state expect "b1000".U
    }
  }

  it should "allow encode FSM states in a binary-gray encoding" in {
    val compiled = compile(new StateMachineWithEncoding(Gray))
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true.B
      dut.clock.step(5)
      dut.reset #= false.B
      dut.io.state expect "b00".U
      dut.clock.step()
      dut.io.state expect "b01".U
      dut.clock.step()
      dut.io.state expect "b11".U
      dut.clock.step()
      dut.io.state expect "b10".U
    }
  }

  it should "allow encode FSM states with user-specified state ids" in {
    val compiled = compile(new Module {
      val io = IO(new Bundle {
        val state = Output(UInt())
      })
      val fsm = new StateMachine {
        val sA = new State(2.U) with EntryPoint
        val sB = new State
        val sC = new State(18.U)
        sA.whenIsActive {
          goto(sB)
        }
        sB.whenIsActive {
          goto(sC)
        }
        sC.whenIsActive {
          goto(sA)
        }
      }
      io.state := fsm.stateCurr
    })
    compiled.runSim { dut =>
      import TestRunnerUtils._
      dut.reset #= true.B
      dut.clock.step(5)
      dut.reset #= false.B
      dut.io.state expect 0.U // BOOT
      dut.clock.step()
      dut.io.state expect 2.U // sA
      dut.clock.step()
      dut.io.state expect 1.U // sB
      dut.clock.step()
      dut.io.state expect 18.U // sC
    }
  }
}
