package chipmunk

import chisel3._

import scala.annotation.nowarn
import scala.collection.mutable.ListBuffer

/** This trait indicates the entry point state of a finite state machine (FSM, [[StateMachine]]). It should be mixed in
  * with a [[State]].
  *
  * There should be only one entry point state in each FSM.
  *
  * @see
  *   [[StateMachine]]
  */
trait EntryPoint extends State

/** State in a finite state machine (FSM, [[StateMachine]]).
  *
  * @param sm
  *   The FSM that this state belongs to. It is implicitly passed by the host FSM.
  *
  * @see
  *   [[StateMachine]]
  */
class State(implicit sm: StateMachineAccessor) {
  private[chipmunk] val tasksWhenIsActive   = ListBuffer[() => Unit]()
  private[chipmunk] val tasksWhenIsInactive = ListBuffer[() => Unit]()
  private[chipmunk] val tasksWhenIsEntering = ListBuffer[() => Unit]()
  private[chipmunk] val tasksWhenIsExiting  = ListBuffer[() => Unit]()

  def whenIsActive(doThat: => Unit): this.type = {
    tasksWhenIsActive += (() => doThat)
    this
  }

  def whenIsInactive(doThat: => Unit): this.type = {
    tasksWhenIsInactive += (() => doThat)
    this
  }

  def whenIsEntering(doThat: => Unit): this.type = {
    tasksWhenIsEntering += (() => doThat)
    this
  }

  def whenIsExiting(doThat: => Unit): this.type = {
    tasksWhenIsExiting += (() => doThat)
    this
  }

  // Add this state to the host FSM
  sm.register(this)
}

/** The entry point of a finite state machine (FSM, [[StateMachine]]). It is the same as `State with EntryPoint`.
  */
final class StateEntryPoint(implicit sm: StateMachineAccessor) extends State with EntryPoint

private[chipmunk] trait StateMachineAccessor {
  def register(state: State): Unit
}

/** Finite state machine (FSM).
  *
  * @note
  *   Each FSM should have one and only one entry point state ([[StateEntryPoint]]).
  *
  * @example
  *   {{{
  *   val fsm = new StateMachine {
  *     val s1 = new State with EntryPoint
  *     val s2 = new State
  *     val s3 = new State
  *
  *     s1
  *       .whenIsActive {
  *         when(io.a) {
  *           goto(s2)
  *         }
  *       }
  *     s2
  *       .whenIsActive {
  *         when(io.b) {
  *           goto(s3)
  *         }
  *       }
  *     s3
  *       .whenIsActive {
  *         when(io.c) {
  *           goto(s1)
  *         }
  *       }
  *   }
  *   }}}
  *
  * @see
  *   [[State]]
  */
@nowarn("""cat=deprecation&origin=scala\.DelayedInit""")
class StateMachine extends StateMachineAccessor with DelayedInit {
  implicit val implicitFsm: StateMachine = this

  private val statesBuffer = ListBuffer[State]()

  def states: List[State] = statesBuffer.toList

  def stateEntry: State = {
    val entries = states.filter(_.isInstanceOf[EntryPoint])
    assert(entries.length == 1, s"There should be 1 entry point in the FSM, but got ${entries.length}.")
    entries.head
  }

  var stateToUInt: Map[State, UInt] = _

  val stateCurr: UInt = Wire(UInt()).dontTouch
  val stateNext: UInt = Wire(UInt()).dontTouch

  /** Transits to the specified state. */
  def goto(state: State): Unit = {
    stateNext := stateToUInt(state)
  }

  /** Returns whether this FSM is in the specified state. */
  def isActive(state: State): Bool = {
    stateCurr === stateToUInt(state)
  }

  /** Returns whether this FSM is NOT in the specified state. */
  def isInactive(state: State): Bool = {
    stateCurr =/= stateToUInt(state)
  }

  /** Returns whether this FSM is entering the specified state. */
  def isEntering(state: State): Bool = {
    stateNext === stateToUInt(state) && stateCurr =/= stateNext
  }

  /** Returns whether this FSM is exiting the specified state. */
  def isExiting(state: State): Bool = {
    stateNext =/= stateToUInt(state) && stateCurr === stateNext
  }

  def register(state: State): Unit = {
    statesBuffer += state
  }

  private var isGenerated: Boolean = false

  def delayedInit(body: => Unit): Unit = {
    body

    // TODO: It is a workaround to avoid FSM generating before States are ready.
    //       I don't know why the delayedInit is called multiple times.
    if (states.isEmpty || isGenerated) return
    isGenerated = true

    stateToUInt = states.zipWithIndex.map { case (state, index) => state -> index.U }.toMap

    stateCurr := RegNext(stateNext, stateToUInt(stateEntry))
    stateNext := stateCurr

    for (state <- states) {
      when(isActive(state)) {
        state.tasksWhenIsActive.foreach(_())
      }
      when(isInactive(state)) {
        state.tasksWhenIsInactive.foreach(_())
      }
      when(isEntering(state)) {
        state.tasksWhenIsEntering.foreach(_())
      }
      when(isExiting(state)) {
        state.tasksWhenIsExiting.foreach(_())
      }
    }
  }
}
