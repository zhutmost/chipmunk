package chipmunk.test

import chisel3.*
import circt.stage.ChiselStage

import chipmunk.*

private object StateMachineSpecDut {
  object States extends ChiselEnum {
    val Idle, Run, Done, Park = Value
  }

  final class Harness extends Module {
    import States.*

    val io = IO(new Bundle {
      val start    = Input(Bool())
      val abort    = Input(Bool())
      val finish   = Input(Bool())
      val clear    = Input(Bool())
      val park     = Input(Bool())
      val self     = Input(Bool())
      val state    = Output(States())
      val active   = Output(Vec(States.all.size, Bool()))
      val entering = Output(Vec(States.all.size, Bool()))
      val leaving  = Output(Vec(States.all.size, Bool()))
      val valid    = Output(Bool())
      val busy     = Output(Bool())
      val done     = Output(Bool())
      val count    = Output(UInt(8.W))
    })

    val count = RegInit(99.U(8.W))
    io.busy := false.B
    io.done := false.B

    val fsm = StateMachine(States)(initial = Idle) {
      on(Idle) {
        when(io.start) { goto(Run) }
        when(io.abort) { goto(Done) }
      }
      on(Run) {
        io.busy := true.B
        count   := count + 1.U
        when(io.abort) { goto(Idle) }
          .elsewhen(io.finish) { goto(Done) }
          .elsewhen(io.self) { goto(Run) }
      }
      on(Done) {
        io.done := true.B
        when(io.clear) { goto(Idle) }
          .elsewhen(io.park) { goto(Park) }
      }
      // Park intentionally has no on block.
    }

    when(fsm.entering(Run)) { count := 0.U }

    io.state := fsm.current
    io.valid := fsm.valid
    io.count := count
    for ((state, index) <- States.all.zipWithIndex) {
      io.active(index)   := fsm.is(state)
      io.entering(index) := fsm.entering(state)
      io.leaving(index)  := fsm.leaving(state)
    }
  }

  object SparseStates extends ChiselEnum {
    val Idle = Value(3.U)
    val Run  = Value(9.U)
    val Done = Value(17.U)
  }

  final class SparseHarness extends Module {
    import SparseStates.*

    val io = IO(new Bundle {
      val finish = Input(Bool())
      val state  = Output(SparseStates())
      val valid  = Output(Bool())
    })

    val fsm = StateMachine(SparseStates)(initial = Run) {
      on(Run) { when(io.finish) { goto(Done) } }
    }
    io.state := fsm.current
    io.valid := fsm.valid
  }

  final class InvalidHarness(mode: String) extends Module {
    val io = IO(new Bundle {
      val dynamic = Input(States())
      val state   = Output(States())
    })

    var later: () => Unit = () => ()
    val initial           = if (mode == "dynamic initial") io.dynamic else States.Idle
    val fsm               = StateMachine(States)(initial = initial) {
      mode match {
        case "duplicate state" =>
          on(States.Idle) {}
          on(States.Idle) {}
        case "nested on" =>
          on(States.Idle) { on(States.Run) {} }
        case "dynamic state" =>
          on(io.dynamic) {}
        case "dynamic target" =>
          on(States.Idle) { goto(io.dynamic) }
        case "escaped goto" =>
          on(States.Idle) { later = () => goto(States.Run) }
        case "dynamic initial" => ()
        case _                 => throw new IllegalArgumentException(s"Unknown test mode: $mode")
      }
    }
    if (mode == "escaped goto") later()
    io.state := fsm.current
  }
}

class StateMachineSpec extends ChipmunkFlatSpec {
  import StateMachineSpecDut.*
  import States.*

  private def initialize(dut: Harness): Unit = {
    dut.io.start #= false.B
    dut.io.abort #= false.B
    dut.io.finish #= false.B
    dut.io.clear #= false.B
    dut.io.park #= false.B
    dut.io.self #= false.B
    dut.reset #= true.B
    dut.clock.step()
    dut.reset #= false.B
  }

  private def expectState(dut: Harness, expected: States.Type): Unit = {
    dut.io.state expect expected
    dut.io.valid expect true.B
    dut.io.busy expect (expected == Run).B
    dut.io.done expect (expected == Done).B
    for ((state, index) <- States.all.zipWithIndex) {
      dut.io.active(index) expect (state == expected).B
    }
  }

  private def expectEvents(
    dut: Harness,
    entering: Option[States.Type] = None,
    leaving: Option[States.Type] = None,
  ): Unit = {
    for ((state, index) <- States.all.zipWithIndex) {
      dut.io.entering(index) expect entering.contains(state).B
      dut.io.leaving(index) expect leaving.contains(state).B
    }
  }

  private def startRun(dut: Harness): Unit = {
    dut.io.start #= true.B
    dut.clock.step()
    dut.io.start #= false.B
    expectState(dut, Run)
    dut.io.count expect 0.U
  }

  "StateMachine" should "reset to the initial state and hold without a transition" in {
    simulate(new Harness) { dut =>
      initialize(dut)
      expectState(dut, Idle)
      expectEvents(dut)
      dut.clock.step(5)
      expectState(dut, Idle)
      expectEvents(dut)
      dut.io.count expect 99.U
    }
  }

  it should "change state on clock edges and expose transition events before those edges" in {
    simulate(new Harness) { dut =>
      initialize(dut)

      dut.io.start #= true.B
      expectState(dut, Idle)
      expectEvents(dut, entering = Some(Run), leaving = Some(Idle))
      dut.io.count expect 99.U
      dut.clock.step()
      dut.io.start #= false.B
      expectState(dut, Run)
      expectEvents(dut)
      dut.io.count expect 0.U

      dut.clock.step(3)
      expectState(dut, Run)
      dut.io.count expect 3.U

      dut.io.finish #= true.B
      expectState(dut, Run)
      expectEvents(dut, entering = Some(Done), leaving = Some(Run))
      dut.clock.step()
      dut.io.finish #= false.B
      expectState(dut, Done)
      expectEvents(dut)
      dut.io.count expect 4.U
      dut.clock.step(2)
      expectState(dut, Done)
      dut.io.count expect 4.U

      dut.io.clear #= true.B
      expectState(dut, Done)
      expectEvents(dut, entering = Some(Idle), leaving = Some(Done))
      dut.clock.step()
      dut.io.clear #= false.B
      expectState(dut, Idle)
      expectEvents(dut)
    }
  }

  it should "produce no entry or exit event for a self transition" in {
    simulate(new Harness) { dut =>
      initialize(dut)
      startRun(dut)
      dut.io.self #= true.B
      expectEvents(dut)
      dut.clock.step(3)
      expectState(dut, Run)
      expectEvents(dut)
      dut.io.count expect 3.U
    }
  }

  it should "give the last active goto priority in separate when blocks" in {
    simulate(new Harness) { dut =>
      initialize(dut)
      dut.io.start #= true.B
      dut.io.abort #= true.B
      expectState(dut, Idle)
      expectEvents(dut, entering = Some(Done), leaving = Some(Idle))
      dut.clock.step()
      dut.io.start #= false.B
      dut.io.abort #= false.B
      expectState(dut, Done)
      expectEvents(dut)
      dut.io.count expect 99.U
    }
  }

  it should "preserve when and elsewhen priority" in {
    simulate(new Harness) { dut =>
      initialize(dut)
      startRun(dut)
      dut.io.abort #= true.B
      dut.io.finish #= true.B
      expectEvents(dut, entering = Some(Idle), leaving = Some(Run))
      dut.clock.step()
      dut.io.abort #= false.B
      dut.io.finish #= false.B
      expectState(dut, Idle)
      expectEvents(dut)
    }
  }

  it should "hold a state whose on block is omitted" in {
    simulate(new Harness) { dut =>
      initialize(dut)
      startRun(dut)
      dut.io.finish #= true.B
      dut.clock.step()
      dut.io.finish #= false.B
      expectState(dut, Done)
      dut.io.park #= true.B
      expectEvents(dut, entering = Some(Park), leaving = Some(Done))
      dut.clock.step()
      expectState(dut, Park)

      dut.io.start #= true.B
      dut.io.abort #= true.B
      dut.io.finish #= true.B
      dut.io.clear #= true.B
      dut.io.self #= true.B
      dut.clock.step(4)
      expectState(dut, Park)
      expectEvents(dut)
    }
  }

  it should "keep synchronous reset priority without masking combinational transition events" in {
    simulate(new Harness) { dut =>
      initialize(dut)
      startRun(dut)
      dut.clock.step(2)
      dut.io.count expect 2.U

      dut.reset #= true.B
      dut.io.start #= true.B
      expectState(dut, Run) // Synchronous reset takes effect at the next edge.
      dut.clock.step()
      expectState(dut, Idle)
      dut.io.count expect 99.U
      expectEvents(dut, entering = Some(Run), leaving = Some(Idle))
      dut.clock.step(3)
      expectState(dut, Idle)
      dut.io.count expect 99.U
      expectEvents(dut, entering = Some(Run), leaving = Some(Idle))

      dut.reset #= false.B
      dut.clock.step()
      dut.io.start #= false.B
      expectState(dut, Run)
      dut.io.count expect 0.U
      expectEvents(dut)
    }
  }

  it should "support sparse ChiselEnum encodings and a non-first initial state" in {
    simulate(new SparseHarness) { dut =>
      dut.io.finish #= false.B
      dut.reset #= true.B
      dut.clock.step()
      dut.reset #= false.B
      dut.io.state expect SparseStates.Run
      dut.io.valid expect true.B
      dut.clock.step(2)
      dut.io.state expect SparseStates.Run
      dut.io.finish #= true.B
      dut.clock.step()
      dut.io.state expect SparseStates.Done
      dut.io.valid expect true.B
    }
  }

  private val invalidCases = Seq(
    "duplicate state" -> "defined more than once",
    "nested on"       -> "must not be nested",
    "dynamic initial" -> "initial state must be a declared enum literal",
    "dynamic state"   -> "requires a declared enum literal",
    "dynamic target"  -> "requires a declared enum literal",
    "escaped goto"    -> "goto must execute while elaborating its on block",
  )

  for ((mode, message) <- invalidCases) {
    it should s"reject $mode during elaboration" in {
      val error = intercept[IllegalArgumentException] {
        ChiselStage.emitCHIRRTL(new InvalidHarness(mode))
      }
      error.getMessage should include(message)
    }
  }
}
