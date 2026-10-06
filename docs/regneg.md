# 🐿️ 下降沿触发寄存器 RegNeg

`chipmunk.RegNegNext` 和 `chipmunk.RegNegEnable` 在时钟下降沿锁存数据。实现使用随库提供的 SystemVerilog 外部模块；时钟和复位来自当前 Chisel 时钟域。

## API

以下签名省略了 `T <: Data` 和 Chisel 的 `SourceInfo` 参数：

```scala
RegNegNext(next: T)
RegNegNext(next: T, init: T, isResetAsync: Boolean)
RegNegEnable(next: T, enable: Bool)
RegNegEnable(next: T, init: T, enable: Bool, isResetAsync: Boolean)
```

| 参数 | 含义 |
| --- | --- |
| `next` | 在下降沿采样的数据 |
| `init` | 复位值，必须是完整 Chisel 字面量，且与 `next` 类型、位宽一致 |
| `enable` | 下降沿为高时更新，否则保持 |
| `isResetAsync` | 有 `init` 时必须显式指定；`true` 使用异步复位，`false` 使用同步复位 |

不带 `init` 的重载不生成复位逻辑。数据必须具有已知的正位宽。带初始化的聚合类型必须是所有字段均指定的 Bundle/Vec literal；运行时信号、`DontCare` 或 `VecInit` 生成的硬件不能充当复位字面量。负数 `SInt` 初始化按该位宽的补码表示。

`isResetAsync` 没有默认值，选择的模式应与当前时钟域的复位类型一致：同步复位在下降沿采样；异步复位不等待时钟沿。

## 示例

```scala
import chisel3.*
import chipmunk.*

class FallingEdgeRegisters extends Module {
  val io = IO(new Bundle {
    val next = Input(SInt(8.W))
    val enable = Input(Bool())
    val noReset = Output(SInt(8.W))
    val syncReset = Output(SInt(8.W))
  })
  io.noReset := RegNegNext(io.next)
  io.syncReset := RegNegEnable(io.next, -3.S(8.W), io.enable, isResetAsync = false)
}
```

异步复位版本应在明确的异步复位域中创建，例如在模块内使用：

```scala
withClockAndReset(clock, reset.asAsyncReset) {
  val value = RegNegNext(next, 0.U(8.W), isResetAsync = true)
}
```

其中 `next` 为应用提供的 8 位 `UInt`。外部仿真或综合流程需包含生成目录里的 RegNeg SystemVerilog 资源。

源码：[RegNeg.scala](../chipmunk/src/RegNeg.scala)。回归测试：

```shell
./mill chipmunk.test.testOnly chipmunk.test.RegNegSpec
```
