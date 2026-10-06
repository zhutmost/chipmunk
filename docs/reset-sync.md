# 🐿️ AsyncResetSync

`chipmunk.AsyncResetSync` 将高有效复位同步到目标时钟域：复位异步置位，释放经过 `stages` 个目标时钟上升沿，默认两级，要求至少两级。

```scala
import chisel3.*
import chipmunk.*

class ResetDomain extends RawModule {
  val clockIn = IO(Input(Clock()))
  val resetIn = IO(Input(AsyncReset()))
  val resetOut = IO(Output(AsyncReset()))
  resetOut := AsyncResetSync.withSpecificClockDomain(clockIn, resetIn, stages = 2)
}
```

## API

| 用法 | 时钟/复位来源 |
| --- | --- |
| `Module(new AsyncResetSync(stages))` | 当前隐式时钟域；模块要求异步复位，输出为 `io.resetOut` |
| `AsyncResetSync(stages = 2)` | 当前隐式时钟域，直接返回 `AsyncReset` 输出 |
| `AsyncResetSync.withSpecificClockDomain(clock, resetAsync, stages = 2)` | 显式时钟域；将输入 reset 转为 `AsyncReset` |

用于创建其他模块的复位域时，在模块内写：

```scala
val syncedReset = AsyncResetSync.withSpecificClockDomain(clock, reset)
withClockAndReset(clock, syncedReset) {
  val value = RegInit(0.U(8.W))
}
```

复位置位时无需时钟运行；释放需要目标时钟，停钟会使已经置位的复位保持。级数描述 RTL 的同步延迟，实际实现还需满足工艺与时序要求。

旧的 `AsyncResetSyncDessert`/`ResetSync` API 已被替换。当前接口没有 `resetChainIn`，也没有锁相环稳定等待或跨域顺序释放控制；有这些需求时，应在系统复位控制器中明确设计，并为每个目标时钟域分别同步释放。

这是复位同步器，不提供数据或总线的跨时钟传输。源码：[AsyncResetSync.scala](../chipmunk/src/AsyncResetSync.scala)。
