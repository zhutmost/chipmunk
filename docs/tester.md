# 🐿️ ChiselSim 测试辅助

`chipmunk.tester` 的 `TesterAPI` 和 `MultiClockSupport` 基于 ChiselSim 的 `PeekPokeAPI`。把这两个 trait 混入自己的 ChiselSim 测试基类即可使用；它们不创建硬件，也不提供旧 chiseltest 的 fork/join 调度。

项目内的 [ChipmunkSim.scala](../chipmunk/test/src/ChipmunkSim.scala) 提供 `ChipmunkFlatSpec`、`ChipmunkFreeSpec` 和 `ChipmunkFunSpec`，用于本仓库测试。这个文件位于 test 源目录，不包含在发布的库中；外部项目需自行定义相应基类。

## 端口 stimulus

`#=` 是 Chisel literal 的 poke 简写，`randomize()` 支持 Bool/UInt/SInt；`expect`、`peek` 和 `clock.step` 使用 ChiselSim API。在继承对应测试基类的 `simulate` 回调内，可以写：

```scala
simulate(new MyDut) { dut =>
  dut.io.input #= 0x12.U
  dut.clock.step()
  dut.io.output.expect(0x12.U)
  dut.io.input.randomize()
}
```

这里 `MyDut` 是应用自己的模块；上例假定它在一拍后输出输入值。`#=` 的右侧需匹配端口 Chisel 类型；AsyncReset 端口可使用对应 Reset literal。随机 UInt 按位宽生成，随机 SInt 覆盖补码有符号范围，零位宽 SInt 被拒绝。

## 多时钟调度

测试 harness 将各 DUT 时钟的电平汇总成一个输入 UInt，再通过每个 bit 的 `.asClock` 驱动各时钟域。另提供一个只推进仿真时间的 `timebaseClock`，不能用它驱动 DUT 的状态。

在混入 `MultiClockSupport` 的 `simulate` 回调中创建一个调度器管理所有域：

```scala
val scheduler = new MultiClockScheduler(
  dut.io.clockLevels,
  dut.clock,
  Seq(
    ClockSpec("fast", periodTicks = 4),
    ClockSpec("slow", periodTicks = 6, firstRiseAt = 4, lowTicks = 2)
  )
)
scheduler.initialize()
scheduler.runEvents(10) { event =>
  println(s"time=${event.time}, levels=${event.levels}")
}
```

示例中的 `dut.clock` 仅作 timebase。`clockLevels(0)` 对应 fast，`clockLevels(1)` 对应 slow；端口位宽必须与域数量完全一致，域名必须唯一。

| ClockSpec 参数 | 语义 |
| --- | --- |
| `periodTicks` | 相邻上升沿间的逻辑 tick 数，至少 2 |
| `lowTicks` | 低相持续时间；默认 `periodTicks / 2` 向下取整，显式值必须在 1 到 period−1 之间 |
| `firstRiseAt` | 第一次上升沿时间；默认与低相持续时间相同，显式值必须大于零 |

所有时钟从低电平开始；奇数周期默认将多出的一 tick 分给高相。一个逻辑 tick 对应两个 simulator timestep。

`advance()` 前进到下一个含时钟沿的时间点，返回 `ClockEvent`；`runEvents(n)` 执行 n 个这样的时间点，而不是 n 个周期。同一时间点的所有时钟沿通过一次 packed poke 同时施加；事件中的 transitions 保持配置顺序，`rising`/`falling` 可过滤边沿。`initialize()` 可重复调用，也会在首次 advance 时自动执行。

## 波形和回归命令

本仓库测试基类通过 ChiselSim CLI options 选择 simulator、FSDB/VPD 和 FST。自定义 `EmitFst` 位于 test 源目录，使用 Verilator；它不是已发布的 `TraceSupport` API。外部测试工程可参考该实现。

```shell
./mill chipmunk.test
./mill chipmunk.test.testOnly chipmunk.test.MultiClockSpec
./mill chipmunk.test.testOnly chipmunk.test.TraceSpec
```

[TraceSpec.scala](../chipmunk/test/src/TraceSpec.scala) 给出 `configMap` 中 `simulator -> verilator`、`emitFst -> 1` 的完整设置。FSDB/VPD 是否可用由仿真后端决定；本仓库的 FST 回归使用 Verilator。

源码：[TesterAPI.scala](../chipmunk/src/tester/TesterAPI.scala)、[MultiClockSupport.scala](../chipmunk/src/tester/MultiClockSupport.scala)。
