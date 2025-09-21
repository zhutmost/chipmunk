<img alt="A Cute Chipmunk" src="https://images.pexels.com/photos/1692984/pexels-photo-1692984.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2" width="100%" height="100%">

# 🐿️ CHIPMUNK: Enhance CHISEL for Smooth and Comfortable Chip Design

![Build & Test](https://github.com/zhutmost/chipmunk/actions/workflows/ci.yml/badge.svg?branch=main)

CHIPMUNK is a Scala package to extend the functionality of [CHISEL](https://chisel-lang.org). It features:
- Extra convenient methods for CHISEL's built-in types,
- Several syntactic sugar to sweeten your CHISEL experience,
- A set of commonly used components and interfaces.

**WARNING**: The code contained in this repo are provided AS IS, and I cannot make any guarantees for availability and correctness. Most of them have only been silicon-verified in academic research (and some even not). You should carefully review every line of code before using it in production.

Please open an issue if you have any questions.

## Installation

CHIPMUNK is an extension of Chisel, so it needs to be used together with CHISEL.

[Mill](https://mill-build.com) is required to build and publish CHIPMUNK.

```shell
mill chipmunk.publishLocal
```

Then add CHIPMUNK to your build file.
```scala
// Mill
def ivyDeps = Agg(..., ivy"com.zhutmost::chipmunk:0.1-SNAPSHOT")
// SBT
libraryDependencies ++= Seq(..., "com.zhutmost" %% "chipmunk" % "0.1-SNAPSHOT")
```

Import it as well as `chisel3` in your Scala RTL code.
```scala
import chisel3._
import chisel3.util._
import chipmunk._
```

## Documentation

CHIPMUNK documents are provided in the [docs folder](docs/README.md).

### Extra convenient Methods for Chisel types
[View detailed document](docs/bits-misc)

Code example:
```scala
val myUInt = Wire(UInt(3.W)).dontTouch // equivalent to `DontTouch(...)`, but more convenient
when(...) {
  myUInt.setAllTo(someBits.lsBit) // set all bits to true or false
} otherwise {
  myUInt.clearAll()
}
val emptyStream = Decoupled(new EmptyBundle) // Bundle without elements
```

### Define Bundle Direction with Master/Slave
[View detailed document](docs/master-slave)

Code example:
```scala
class AxiIO extends Bundle with IsMasterSlave {
  val aw = Master(new AxiWriteAddrChannelIO)
  val ar = Master(new AxiReadAddrChannelIO)
  val r = Slave(new AxiReadDataChannelIO)
  val w = Master(new AxiWriteDataChannelIO)
  val b = Slave(new AxiWriteRespChannelIO)
  def isMaster = true // indicate this bundle is a Master
}

class AxiSlave extends Module {
  val io = IO(new Bundle {
    val axi = Slave(new AxiIO) // automatically flip the signal directions
  })
  // ...
}
```

### Registers triggered on the falling clock edge
[View detailed document](docs/regneg)

Code example:
```scala
withClockAndReset(clock, reset) {
  val regNeg1 = RegNegNext(nextVal)
  val regNeg2 = RegNegEnable(nextVal, initVal, enable)
}
```

### Finite State Machine

[View detailed document]()

Code example:

```scala
val fsm = new StateMachine {
  val s1 = new State with EntryPoint
  val s2 = new State
  s1
    .whenIsActive {
      when(io.a) {
        goto(s2)
      }
    }
  s2
    .whenIsActive {
      when(io.b) {
        goto(s1)
      }
    }
}
io.out := fsm.isExiting(fsm.s2)
```

### Asynchronous Reset Synchronous Dessert
[View detailed document]()

Code example:
```scala
val reset1 = AsyncResetSyncDessert.withImplicitClockDomain()
val reset2 = AsyncResetSyncDessert.withSpecificClockDomain(clockSys, coreReset, resetChainIn = reset1)
```

### StreamIO/FlowIO: Decouple Dataflow with Handshake
[View detailed document](docs/stream)

Code example:
```scala
TODO
```

### Clock Domain Crossing Blocks
[View detailed document]()

Code example:
```scala
TODO
```

(Not all above document pages are ready yet.)

I am sorry they are written in Chinese (Machine translation driven by AI is good enough now :D).

## Acknowledgement

CHIPMUNK is standing on the shoulder of giants.
Thanks for [CHISEL](https://chisel-lang.org), [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL) and many other open-sourced projects.
