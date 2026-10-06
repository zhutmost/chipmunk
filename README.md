<img alt="A Cute Chipmunk" src="https://images.pexels.com/photos/1692984/pexels-photo-1692984.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2" width="100%" height="100%">

# 🐿️ CHIPMUNK: Enhance CHISEL for Smooth and Comfortable Chip Design

![Build & Test](https://github.com/zhutmost/chipmunk/actions/workflows/ci.yml/badge.svg?branch=main)

CHIPMUNK extends [Chisel](https://chisel-lang.org) with convenient hardware APIs, interfaces, and reusable components:

- Bits/Data extensions, priority selection, Master/Slave directions, and flat RTL interfaces.
- Falling-edge registers, enum-based state machines, and asynchronous-assert/synchronous-deassert reset synchronization.
- Stream/Flow pipelines, queues, routing, and arbitration.
- Acorn routing, SRAM and width adapters, register banks, AXI4/AXI4-Lite bridges, and an SPI debugger.
- ChiselSim helpers for port stimulus and multiple simulated clocks.

**WARNING**: This code is provided AS IS, without guarantees of availability or correctness. Review and verify each component in its intended integration before production use. Simulation coverage does not establish silicon verification of the current implementation.

Please open an issue if you have any questions.

## Build and installation

This source tree targets Scala 3. The versions currently selected in [build.mill](build.mill) are:

| Component | Version |
| --- | --- |
| CHIPMUNK | `0.2.0-SNAPSHOT` |
| Scala | `3.8.4` |
| Chisel library and compiler plugin | `7.16.0+3-a2d66312-SNAPSHOT` |
| ScalaTest | `3.2.20` |
| Bundled Mill launcher default | `1.1.9` |

Use the same Chisel version for the library and compiler plugin, with the plugin cross-published for the full Scala version. The snapshot repository is configured in `build.mill`; replacing this dependency with another Chisel build requires checking both Scala 3 artifacts and compiler-plugin compatibility.

Run commands from the repository root. Tests use Verilator and require it on `PATH`.

```shell
./mill __.compile
./mill chipmunk.test
./mill mill.scalalib.scalafmt/checkFormatAll
./mill mylib.run
```

The last command emits the example design into `generate/hw`. To use CHIPMUNK from another project, first publish this checkout to the local Ivy repository:

```shell
./mill chipmunk.publishLocal
```

This publishes `com.zhutmost:chipmunk_3:0.2.0-SNAPSHOT` locally; it does not publish the package to a public repository. An example Mill 1.x consumer `build.mill` is:

```scala
package build

import mill.scalalib.*

object rtl extends ScalaModule {
  def scalaVersion = "3.8.4"
  val chiselVersion = "7.16.0+3-a2d66312-SNAPSHOT"

  override def repositories = super.repositories() ++ Seq(
    "ivy2Local",
    "https://central.sonatype.com/repository/maven-snapshots"
  )
  override def mvnDeps = super.mvnDeps() ++ Seq(
    mvn"org.chipsalliance::chisel:$chiselVersion",
    mvn"com.zhutmost::chipmunk:0.2.0-SNAPSHOT"
  )
  override def scalacPluginMvnDeps = super.scalacPluginMvnDeps() ++ Seq(
    mvn"org.chipsalliance:::chisel-plugin:$chiselVersion"
  )
}
```

See the [Mill publishing guide](https://mill-build.org/mill/1.0.x/javalib/publishing.html) for local publishing options.

Import the packages needed by your design:

```scala
import chisel3.*
import chisel3.util.*
import chipmunk.*
import chipmunk.stream.*
import chipmunk.acorn.*
import chipmunk.amba.*
import chipmunk.regbank.*
import chipmunk.spi.*
```

## Examples

The following hardware snippets belong inside a Chisel Module, with the imports above.

Bits/Data extensions:

```scala
val word = Wire(UInt(8.W)).dontTouch
word := word.filledWith(true)
val lowNibble = word.lsBits(4)
val selected = PriorityEncoderDefault(word, default = 8.U(4.W))
```

A Stream pipeline with a transformed payload:

```scala
class StreamIncrement extends Module {
  val io = IO(new Bundle {
    val in = Slave(Stream(UInt(8.W)))
    val out = Master(Stream(UInt(8.W)))
  })
  io.out << io.in.payloadMap(_ + 1.U).pipeAll()
}
```

An enum-based state machine:

```scala
object States extends ChiselEnum {
  val Idle, Run = Value
}
val fsm = StateMachine(States)(States.Idle) {
  on(States.Idle) {
    when(start) { goto(States.Run) }
  }
  on(States.Run) {
    when(done) { goto(States.Idle) }
  }
}
val busy = fsm.is(States.Run)
```

`start` and `done` above are application-provided `Bool` signals. Falling-edge registers use `RegNegNext(next)` or `RegNegEnable(next, enable)`; initialized overloads also require an explicit `isResetAsync` argument. Reset synchronization uses `AsyncResetSync.withSpecificClockDomain(clock, resetAsync, stages = 2)`.

## Documentation

The [documentation index](docs/README.md) links all reference pages. Documentation is primarily in Chinese, with some English pages.

| Area | Documentation |
| --- | --- |
| Bits/Data, priority selection, and basic records | [Bits/Data helpers](docs/bits-misc.md) |
| Interface directions and RTL port names | [Master/Slave](docs/master-slave.md), [VerilogIO](docs/verilog-io.md) |
| Registers, state machines, and reset | [RegNeg](docs/regneg.md), [StateMachine](docs/state-machine.md), [AsyncResetSync](docs/reset-sync.md) |
| Stream/Flow | [Handshake and components](docs/stream.md) |
| Acorn | [Protocol, routing, SRAM, and width adaptation](docs/acorn.md) |
| AMBA bridges | [AXI4/AXI4-Lite and Acorn conversion](docs/amba.md) |
| Register endpoints and debug | [RegBank](docs/regbank.md), [SPI debugger](docs/spi-debugger.md) |
| Verification helpers | [ChiselSim and multiple clocks](docs/tester.md) |

## Acknowledgement

CHIPMUNK is standing on the shoulders of giants. Thanks to [Chisel](https://chisel-lang.org), [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL), and many other open-source projects.
