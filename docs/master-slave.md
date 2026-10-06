# 🐿️ 用 Master/Slave 定义接口方向

`chipmunk.IsMasterSlave` 为 `Record`（包括 `Bundle`）声明接口的 master/slave 视角。`Master(...)` 和 `Slave(...)` 根据这个声明保留或翻转字段方向，使使用方可以直接表达当前端口的角色。

```scala
import chisel3.*
import chipmunk.*

class SramWriteIO extends Bundle with IsMasterSlave {
  val enable = Input(Bool())
  val addr = Input(UInt(16.W))
  val dataIn = Input(UInt(32.W))
  override def isMaster: Boolean = false
}

class SramWriter extends Module {
  val io = IO(new Bundle {
    val write = Master(new SramWriteIO)
  })
  io.write.enable := false.B
  io.write.addr := 0.U
  io.write.dataIn := 0.U
}
```

这里 `SramWriteIO` 按 slave 视角声明，因此 `Master(new SramWriteIO)` 将三个输入翻转为输出；`Slave(new SramWriteIO)` 则保持声明方向。包装后仍需用 `IO(...)`、`Wire(...)` 等将类型绑定为硬件。

## 声明视角与实际端口方向

`isMaster` 描述类型定义中的字段方向，不查询一个已绑定端口的实际方向，也不会因为套上 `Slave(...)` 就改写该声明。需要检查实际方向时，应使用 Chisel 的方向查询 API。

同一个 `Record` 实例不能被 `Master`/`Slave` 重复包装，例如 `Master(Slave(new SramWriteIO))` 会被拒绝。各个端口应分别创建新实例；不同层级的子接口可以各自包装。

## 嵌套接口

AXI 的地址、写数据由 master 发送，读数据和写响应由 slave 发送；这种多通道接口不能简单理解为所有字段都属于同一个数据生产者。现有的 `AxiIO` 已声明完整方向，可以直接使用：

```scala
import chipmunk.amba.*

val masterPort = IO(Master(new AxiIO(AxiParams(dataWidth = 64, addrWidth = 32, idWidth = 4))))
val slavePort = IO(Slave(new AxiIO(AxiParams(dataWidth = 64, addrWidth = 32, idWidth = 4))))
```

以上两行位于模块内，展示端口声明；各通道还需连接到应用逻辑。对于 Stream/Flow、Acorn、AXI 和 SPI，可以沿用相同的角色包装方式。

源码：[IsMasterSlave.scala](../chipmunk/src/IsMasterSlave.scala)。展平端口时的方向处理见 [VerilogIO](verilog-io.md)。
