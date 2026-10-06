# 🐿️ StateMachine

`chipmunk.StateMachine` 使用 `ChiselEnum` 描述状态，在当前时钟/复位域生成状态寄存器。状态定义和跳转是 elaboration 时构建的电路；没有跳转时保持当前状态。

```scala
import chisel3.*
import chipmunk.*

class Controller extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done = Input(Bool())
    val busy = Output(Bool())
    val enteringRun = Output(Bool())
    val leavingRun = Output(Bool())
  })
  object States extends ChiselEnum {
    val Idle, Run = Value
  }
  val fsm = StateMachine(States)(States.Idle) {
    on(States.Idle) {
      when(io.start) { goto(States.Run) }
    }
    on(States.Run) {
      when(io.done) { goto(States.Idle) }
    }
  }
  io.busy := fsm.is(States.Run)
  io.enteringRun := fsm.entering(States.Run)
  io.leavingRun := fsm.leaving(States.Run)
}
```

## 查询与事件

| API | 含义 |
| --- | --- |
| `fsm.current` | 当前状态，只读 |
| `fsm.is(state)` | 当前状态是否为指定状态 |
| `fsm.entering(state)` | 当前状态不是该状态，组合下一状态是该状态 |
| `fsm.leaving(state)` | 当前状态是该状态，组合下一状态不是该状态 |
| `fsm.valid` | 当前状态是否为声明的枚举值之一 |

`entering`/`leaving` 在执行跳转的时钟沿之前组合有效，不是时钟沿之后的延迟脉冲。跳转到自身不会产生这两个事件。查询逻辑不受 reset 门控，复位期间也可能随输入变化；应用需要复位时禁止输出事件时，应显式门控。

状态寄存器复位到 `initial`。`valid` 仅报告有效性，不提供非法状态自动恢复机制。

## 构建约束

- 在模块内无条件创建 StateMachine，不要把整个构建过程放在硬件 `when`/`switch` 内。
- `initial`、`on`、`goto` 和查询目标必须是同一个枚举里声明的状态字面量。
- 每个状态最多一个 `on`，不允许嵌套 `on`；没有 `on` 的状态仍可保持。
- `goto` 只能出现在 `on` 内，可以由 Chisel `when` 条件控制。同一周期有多个有效跳转赋值时遵循 Chisel 最后连接优先规则。
- Builder/StateScope 由 Scala 3 上下文参数传递；通常通过 `import chipmunk.*` 使用导出的 `on`/`goto` 即可。

源码：[StateMachine.scala](../chipmunk/src/StateMachine.scala)。验证：

```shell
./mill chipmunk.test.testOnly chipmunk.test.StateMachineSpec
```
