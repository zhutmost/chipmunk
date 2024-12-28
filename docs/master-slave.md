# 🐿️ 用 Master/Slave 定义 Bundle 方向

`chipmunk.IsMasterSlave` 使用户在定义 `Bundle` 时可同时规定其属于数据的生产者（Master）或消费者（Slave）。当以 `Master()` 定义的 `Bundle` 被以 `Slave()` 形式例化时，内部信号的方向会自动翻转，反之亦然。

## Why？
`chisel3.Bundle` 在例化时，可以使用 `Flipped()` 翻转其内部信号的方向。在许多Chisel项目中，我们约定 `Bundle` 作为模块端口时默认是生产者，如果我们希望它作为消费者，则显式地用 `Flipped()` 把它包裹起来。然而，很多用户并不总是遵守这一约定，所以用户经常搞不清楚端口需不需要用 `Flipped()` 包裹起来。实践中，对于别人代码中的模块接口，通常需要阅读其代码和文档才能判断这些 `Bundle` 属于 Master 还是 Slave（是否需要翻转其方向）。

例如，如下代码片段中的 SRAM 读写接口 `SramReadWriteIO` 看起来非常合理，但根据上述约定，这一接口既然属于Slave，其内部信号方向应该反过来定义（比如 `enable` 应当是 `Output()`）。不遵守约定的结果，就是其他用户在调用这一 `Bundle` 的的时候经常会陷入困惑：我应不应该套上 `Flipped()`？

```scala
class SramReadWriteIO extends Bundle {
  val enable  = Input(Bool())
  val read    = Input(Bool())
  val addr    = Input(UInt(16.W))
  val dataIn  = Input(UInt(32.W))
  val dataOut = Output(UInt(32.W))
}
```

## Usage

`chipmunk.IsMasterSlave` 是一个特质，用户在定义 `Bundle` 时可以选择混入这一特质。它要求额外定义一方法 `def isMaster: Boolean`，`true` 表示该 `Bundle` 当前的信号方向属于 Master，反之属于 Slave。

```scala
class SramWriteIO extends Bundle with IsMasterSlave {
  val enable  = Input(Bool())
  val addr    = Input(UInt(16.W))
  val dataIn  = Input(UInt(32.W))
  def isMaster = false // This is a Slave Bundle
}
```

如此一来，在例化它时，用户可以用 `Master()` 或 `Slave()` 来选择它内部信号的方向。对于一个套上 `Slave()` 的 Master `Bundle`，其内部信号方向会相应取反。用户不需要关心何时需要 `Flipped`，只需要记住这一 `Bundle` 在当前模块的数据传输关系。

```scala
class Sram extends Module {
  val io = new Bundle {
    val rw = Slave(new SramReadWriteIO)
  }
  // ...
}
```

此外，`Master`/`Slave` 还支持嵌套使用，从而允许用户构造出如下 AMBA AXI 总线这样的复杂例子。

```scala
class AxiIO extends Bundle with IsMasterSlave {
  val aw = Master(new AxiWriteAddrChannelIO)
  val ar = Master(new AxiReadAddrChannelIO)
  val r = Slave(new AxiReadDataChannelIO)
  val w = Master(new AxiWriteDataChannelIO)
  val b = Slave(new AxiWriteRespChannelIO)
  def isMaster = true
}

class AxiSlave extends Module {
  val io = IO(new Bundle {
    val axi = Slave(new AxiIO)
  })
  // ...
}
```
