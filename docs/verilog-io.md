# 🐿️ VerilogIO：展平 RTL 端口

`HasVerilogIO` 为接口的叶字段生成 RTL 名称；`createVerilogIO` 从未绑定的接口类型构造展平 Record；绑定后用 `viewAsChiselIO` 恢复原来的 Chisel 接口 API。视图只提供映射，不增加寄存器、缓冲或延迟。

以下完整模块把 AXI4 slave 端口展平，并连接到 Acorn 桥：

```scala
import chisel3.*
import chipmunk.*
import chipmunk.acorn.*
import chipmunk.amba.*

class FlatAxiBridge extends Module {
  val params = AxiParams(dataWidth = 32, addrWidth = 16, idWidth = 2)
  val axiRtl = FlatIO(Slave(new AxiIO(params)).createVerilogIO(name => s"S_AXI_$name"))
  val acorn = IO(Master(new AcornIO(AcornParams(32, 16))))

  val bridge = Module(new AxiToAcornBridge(params))
  bridge.io.sAxi :<>= axiRtl.viewAsChiselIO
  acorn :<>= bridge.io.mAcorn
}
```

AXI/AXI-Lite 已实现叶字段命名，例如 `aw.bits.addr` → `AWADDR`、`aw.valid` → `AWVALID`；上述 rename 函数进一步得到 `S_AXI_AWADDR`。`FlatIO` 避免增加外层 Bundle 名字；普通 `IO(...)` 也可绑定，但生成的端口名可能包含根接口前缀。模块的 clock/reset 不属于这个 AXI 接口，不受该 rename 函数影响。

## 自定义接口与方向

自定义接口混入 `HasVerilogIO` 并实现 `generatePortName(path: Seq[String]): String`；路径是相对接口根节点的字段路径，Vec 下标以十进制字符串表示。默认 rename 为 identity。

- 源类型必须未绑定；不要把已有 `IO`/`Wire` 传给 `createVerilogIO`。
- `viewAsChiselIO` 的调用对象必须已经绑定为硬件，不能在未绑定类型上建立视图。
- 支持 Record/Vec 内的数字叶字段，包括 Bits、枚举、Clock 和 Reset；不支持 Analog、Probe、opaque Record 或别名叶字段。
- 生成并重命名后的叶名称必须唯一，并满足 `[A-Za-z_][A-Za-z0-9_]*`。
- 原接口含 `IsMasterSlave` 时，展平类型保留相应能力；角色包装仍只能对同一个实例应用一次。通常像上例一样先指定 `Slave(...)`，再展平。
- 视图跟随实际绑定时的方向翻转/强制方向，不通过原类型的 `isMaster` 去猜测已绑定端口。

这是 Scala 3 类型工厂 API，旧的手动 `new VerilogIO` 用法需要迁移。源码：[VerilogIO.scala](../chipmunk/src/VerilogIO.scala)、[VerilogIOFactory.scala](../chipmunk/src/VerilogIOFactory.scala)。验证：

```shell
./mill chipmunk.test.testOnly chipmunk.test.VerilogIOSpec
```
