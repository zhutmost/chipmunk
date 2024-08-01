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

  def whenIsActive(body: => Unit): this.type = {
    tasksWhenIsActive += (() => body)
    this
  }

  def whenIsInactive(body: => Unit): this.type = {
    tasksWhenIsInactive += (() => body)
    this
  }

  def whenIsEntering(body: => Unit): this.type = {
    tasksWhenIsEntering += (() => body)
    this
  }

  def whenIsExiting(body: => Unit): this.type = {
    tasksWhenIsExiting += (() => body)
    this
  }

  // Add this state to the host FSM
  sm.register(this)
}

/** The entry point of a finite state machine (FSM, [[StateMachine]]). It is the same as `State with EntryPoint`.
  */
final class StateEntryPoint(implicit sm: StateMachineAccessor) extends State with EntryPoint

/** Provides the finite state machine ([[StateMachine]], FSM) APIs visible to its states. */
private[chipmunk] trait StateMachineAccessor {
  def register(state: State): Unit
}

object Sequential extends StateMachineEncoding {
  def encode(index: Int): Int = index

  def stateToUInt(states: List[State]): Map[State, UInt] = {
    states.zipWithIndex.map { case (state, index) => state -> encode(index).U }.toMap
  }
}

object OneHot extends StateMachineEncoding {
  def encode(idx: Int): Int = 1 << idx

  def stateToUInt(states: List[State]): Map[State, UInt] = {
    states.zipWithIndex.map { case (state, index) => state -> encode(index).U }.toMap
  }
}

object Gray extends StateMachineEncoding {
  def encode(idx: Int): Int = idx ^ (idx >> 1)

  def stateToUInt(states: List[State]): Map[State, UInt] = {
    states.zipWithIndex.map { case (state, index) => state -> encode(index).U }.toMap
  }
}

abstract class StateMachineEncoding {
  def stateToUInt(states: List[State]): Map[State, UInt]
}

/** Finite state machine (FSM).
  *
  * @param autoStart
  *   Whether the FSM should automatically start (i.e., transit to the entry point state) when the reset is de-asserted.
  *   The default value is true.
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
class StateMachine(val autoStart: Boolean = true, defaultEncoding: StateMachineEncoding = Sequential)
    extends StateMachineAccessor
    with DelayedInit {
  implicit val implicitFsm: StateMachine = this

  // When a State is instantiated, it will call register() to add itself to statesBuffer.
  private val statesBuffer = ListBuffer[State]()

  def states: List[State] = statesBuffer.toList

  // The default state when the FSM is reset. The FSM will transit from this state to the entry point when startFsm() is
  // called.
  val stateBoot: State = new State {
    if (autoStart) {
      whenIsActive {
        startFsm()
      }
    }
  }

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
    stateNext === stateToUInt(state) && stateCurr =/= stateToUInt(state)
  }

  /** Returns whether this FSM is exiting the specified state. */
  def isExiting(state: State): Bool = {
    stateNext =/= stateToUInt(state) && stateCurr === stateToUInt(state)
  }

  /** Add a state to the FSM. This method is automatically called by the constructor of [[State]]. */
  def register(state: State): Unit = {
    statesBuffer += state
  }

  /** Transit to the entry point state. */
  def startFsm(): Unit = {
    goto(stateEntry)
  }

  /** Transit back to the BOOT state. */
  def finishFsm(): Unit = {
    goto(stateBoot)
  }

  private var isGenerated: Boolean = false

  def delayedInit(body: => Unit): Unit = {
    body

    // TODO: It is a workaround to avoid FSM generating before States are ready.
    //       I don't know why the delayedInit is called multiple times.
    if (states.length <= 1 || isGenerated) return
    isGenerated = true

    stateToUInt = defaultEncoding.stateToUInt(states)

    stateCurr := RegNext(stateNext, stateToUInt(stateBoot))
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
