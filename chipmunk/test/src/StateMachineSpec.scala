package chipmunk.test

import chipmunk._
import chipmunk.tester._
import chisel3._

class StateMachineSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "StateMachine" should "work in a loose style" in {
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
      io.state := fsm.stateCurr
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
      dut.io.state expect 0.U
      dut.io.a #= true.B
      //      dut.io.bIsEntering expect true.B
      dut.clock.step(2)
      dut.io.state expect 1.U
      dut.io.b #= true.B
      dut.clock.step()
      dut.io.state expect 2.U
      dut.io.a #= false.B
      dut.io.b #= false.B
      dut.clock.step()
      dut.io.state expect 2.U
      dut.io.cIsActive expect true.B
      dut.io.c #= true.B
      //      dut.io.cIsExiting expect true.B
      dut.clock.step()
      dut.io.state expect 0.U
      dut.io.c #= false.B
    }
  }
}
