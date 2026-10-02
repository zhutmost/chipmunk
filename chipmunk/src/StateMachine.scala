package chipmunk

import scala.collection.mutable

import chisel3.*
import chisel3.experimental.SourceInfo

/** A finite state machine whose states are literals of a ChiselEnum.
  *
  * The state register follows the clock and reset signals at the StateMachine call site. Reset selects the initial
  * state directly. States without a transition hold their value; self-transitions do not produce entry or exit events.
  */
final class StateMachine[S <: EnumType] private (
  private val states: Vector[S],
  private val stateReg: S,
  private val stateNext: S,
) {

  /** A read-only view of the current state. */
  def current(using SourceInfo): S = stateReg.readOnly

  /** Returns True while the current state is s. */
  def is(s: S)(using SourceInfo): Bool = {
    checkState(s)
    stateReg === s
  }

  /** Returns True before the clock edge that changes the state from another state to s. */
  def entering(s: S)(using SourceInfo): Bool = {
    checkState(s)
    stateReg =/= s && stateNext === s
  }

  /** Returns True before the clock edge that changes the state from s to another state. */
  def leaving(s: S)(using SourceInfo): Bool = {
    checkState(s)
    stateReg === s && stateNext =/= s
  }

  /** True when the state register contains a declared enum value. */
  def valid(using SourceInfo): Bool = stateReg.isValid

  private def checkState(s: S)(using sourceInfo: SourceInfo): Unit = {
    require(
      states.exists(_ eq s),
      s"StateMachine requires a declared enum literal, got $s${sourceInfo.makeMessage(location => s" $location")}.",
    )
  }
}

object StateMachine {

  /** Build an FSM immediately in the current Chisel clock/reset scope.
    *
    * Define the FSM outside hardware when/switch blocks. An omitted on block means the corresponding state holds. Each
    * state may have at most one on block. Ordinary Chisel last-connect priority applies to goto assignments.
    */
  def apply[E <: ChiselEnum](states: E)(
    initial: states.Type
  )(body: Builder[states.Type] ?=> Unit)(using sourceInfo: SourceInfo): StateMachine[states.Type] = {
    val values = states.all.toVector
    require(values.nonEmpty, "StateMachine requires at least one state.")
    require(
      values.exists(_ eq initial),
      s"StateMachine initial state must be a declared enum literal${sourceInfo.makeMessage(location => s" $location")}.",
    )

    val stateReg  = RegInit(initial).suggestName("stateCurr")
    val stateNext = WireDefault(stateReg).suggestName("stateNext")
    val machine   = new StateMachine[states.Type](values, stateReg, stateNext)

    stateReg := stateNext

    val builder = new Builder[states.Type](machine)
    try body(using builder)
    finally builder.close()

    machine
  }

  /** Generate hardware enabled while the FSM is in s. */
  def on[S <: EnumType](s: S)(body: StateScope[S] ?=> Unit)(using builder: Builder[S], sourceInfo: SourceInfo): Unit =
    builder.define(s)(body)

  /** Select the state for the next clock edge. Only available within an on block. */
  def goto[S <: EnumType](target: S)(using scope: StateScope[S], sourceInfo: SourceInfo): Unit =
    scope.transition(target)

  /** Context supplied to a StateMachine definition block. */
  final class Builder[S <: EnumType] private[StateMachine] (machine: StateMachine[S]) {
    private val defined     = mutable.HashSet.empty[BigInt]
    private var open        = true
    private var inStateBody = false

    private[StateMachine] def define(s: S)(body: StateScope[S] ?=> Unit)(using sourceInfo: SourceInfo): Unit = {
      val location = sourceInfo.makeMessage(value => s" $value")
      require(open, s"StateMachine definition has already finished$location.")
      require(!inStateBody, s"StateMachine on blocks must not be nested$location.")
      machine.checkState(s)
      require(defined.add(s.litValue), s"StateMachine state $s is defined more than once$location.")

      val scope = new StateScope[S](machine)
      inStateBody = true
      try {
        when(machine.stateReg === s) {
          body(using scope)
        }
      } finally {
        scope.close()
        inStateBody = false
      }
    }

    private[StateMachine] def close(): Unit = open = false
  }

  /** Context supplied only while elaborating an on block. */
  final class StateScope[S <: EnumType] private[StateMachine] (machine: StateMachine[S]) {
    private var open = true

    private[StateMachine] def transition(target: S)(using sourceInfo: SourceInfo): Unit = {
      require(
        open,
        s"StateMachine goto must execute while elaborating its on block${sourceInfo.makeMessage(value => s" $value")}.",
      )
      machine.checkState(target)
      machine.stateNext := target
    }

    private[StateMachine] def close(): Unit = open = false
  }
}

export StateMachine.{goto, on}
