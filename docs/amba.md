# 🐿️ AXI4 / AXI4-Lite 与 Acorn 桥接

使用 `import chipmunk.amba.*`，方向包装见 [Master/Slave](master-slave.md)。所有桥接模块的两端共用时钟和复位，不承担时钟域转换；复位取消在途事务，需要连接端点一致复位。

## 接口参数

| 参数 | AXI4 (`AxiParams`) | AXI4-Lite (`AxiLiteParams`) |
| --- | --- | --- |
| `dataWidth` | 8～1024 位的二次幂 | 32 或 64 位 |
| `addrWidth` | 正整数，字节地址宽度 | 正整数，字节地址宽度 |
| `strobeWidth` | `dataWidth / 8`，派生值 | `dataWidth / 8`，派生值 |
| `idWidth` | 默认 0，此时 ID 字段为 `None` | 无 ID |
| `hasQos` / `hasRegion` | 默认 false，启用后为 `Some` 字段 | 无对应字段 |

`AxiIO(params)` 和 `AxiLiteIO(params)` 包含 AW/W/B/AR/R 五个 Stream 通道，按 master 视角声明。AXI4 的 `fullSize` 是完整数据字对应的 AxSIZE 编码。可选字段必须通过 `foreach`/`get` 等处理，不能当作无条件存在的 UInt。

## 桥接选择

| 模块 | 输入 → 输出 | 事务与容量 |
| --- | --- | --- |
| `AcornToAxiBridge` | `io.sAcorn` → `io.mAxi` | 单拍完整字访问；读写各一个在途命令，包含响应等待时间 |
| `AcornToAxiLiteBridge` | `io.sAcorn` → `io.mAxiLite` | 复用上面的事务控制，投影为 AXI4-Lite |
| `AxiLiteToAxiBridge` | `io.sAxiLite` → `io.mAxi` | 五通道组合映射，不增加缓冲或延迟 |
| `AxiToAcornBridge` | `io.sAxi` → `io.mAcorn` | 每方向一个 AXI burst，拆成有序 Acorn 命令；内部 Acorn 在途量可配置 |

这些模块保持地址和数据位宽。需要 Acorn 数据位宽变化时，另外使用 [AcornWidthAdapter](acorn.md#acornwidthadapter)。

## Acorn → AXI4 / AXI4-Lite

```scala
import chisel3.*
import chipmunk.*
import chipmunk.acorn.*
import chipmunk.amba.*

class AcornAxiBridge extends Module {
  val params = AxiParams(dataWidth = 64, addrWidth = 32, idWidth = 4)
  val io = IO(new Bundle {
    val acorn = Slave(new AcornIO(AcornParams(64, 32)))
    val axi = Master(new AxiIO(params))
  })
  val bridge = Module(new AcornToAxiBridge(params, writeId = 1, readId = 2))
  bridge.io.sAcorn :<>= io.acorn
  io.axi :<>= bridge.io.mAxi
}
```

AW 与 W 独立完成握手；输入命令和返回响应均缓冲，并在背压期间保持稳定。读写独立进行，新命令要等本方向的上一响应被 Acorn 接收。

- 仅发送单拍、自然对齐、完整字大小的普通访问：AxLEN=0、AxSIZE=`fullSize`、INCR、LOCK=0。
- 未对齐的 Acorn 命令在本地产生错误，不访问 AXI；该类读错误返回零。AXI 返回错误时，Acorn 的 data 保留 AXI 返回值，使用方必须检查 error。
- 字节写掩码原样传递，包括全零 strobe，不做读改写。
- `writeId` / `readId` 为静态 ID（默认 0）；`writeProt` / `readProt` 默认 0，范围为 0～7。CACHE/QoS/REGION 为零。
- OKAY 映射为成功，其他 RESP 映射为错误。意外的 ID、读响应缺少 RLAST、普通访问返回 EXOKAY 会触发断言。

`AcornToAxiLiteBridge(AxiLiteParams(...), writeProt, readProt)` 沿用这些事务语义，接口没有 ID。

### 直接桥与经 AXI-Lite 组合的区别

`AcornToAxiLiteBridge + AxiLiteToAxiBridge` 使用相同的 Acorn 事务控制，额外的 Lite → AXI 投影没有流水延迟。二者都产生单拍访问，组合方案不会因此获得 burst 或更多在途能力。

直接 `AcornToAxiBridge` 支持 AXI4 的全部上述数据宽度，可直接配置 ID/PROT。组合路径受 AXI4-Lite 的 32/64 位限制；ID 在 Lite → AXI 层配置。已有 AXI-Lite 连接点时可使用组合路径，直接接 AXI4 时使用直接桥更简单。

## AXI4-Lite → AXI4

`AxiLiteToAxiBridge(params, writeId = 0, readId = 0)` 的 `params` 为 `AxiParams`，但数据宽度因 Lite 侧限制只能为 32/64。它组合连接五个通道，AW/W 保持独立，添加固定 ID、单拍完整字大小、INCR、LOCK=0，以及零 CACHE/QoS/REGION。PROT 和 RESP 原样传递。

本模块不自行限制在途事务数；连接的 master/slave 决定容量。同一方向使用固定 ID 保持顺序，返回 ID、RLAST、EXOKAY 有相应断言。

## AXI4 → Acorn

`AxiToAcornBridge(params, outstanding = AcornOutstanding())` 支持自然对齐、完整字大小的 INCR/FIXED/WRAP burst，含部分或全零 WSTRB。每个读响应保留原 ARID，写响应保留原 AWID。

- 窄访问、未对齐或地址溢出的访问返回 SLVERR，不向 Acorn 发命令。拒绝的读仍返回要求数量的 R beat，data 为零；拒绝的写仍接收整段 W 数据后返回 B。
- Acorn 读错误逐拍映射为 SLVERR，data 原样返回；写错误在整个 burst 内累积，所有写响应收齐后才生成 B。已经完成的写不会回滚。
- `outstanding.read` 同时覆盖待返回的 Acorn 读和已缓存但 AXI 尚未接收的 R beat；`outstanding.write` 限制未收齐的 Acorn 写响应。默认各为 4。
- W 入口有两项队列，可先于 AW 到达。每方向只允许一个活动 burst，读写之间独立，不提供跨方向排序。
- PROT/CACHE/QoS/REGION 不解释；exclusive 作为普通访问执行，不返回 EXOKAY。不能借此实现 exclusive 原子语义。
- 超过总线宽度的 AxSIZE、非法 burst 类型/长度、跨 4KB、WRAP 起始对齐错误和不一致 WLAST 属于协议违规，使用断言检查；不能把它们当作可依赖的正常错误处理接口。

该桥拆分 burst，不把多个 beat 合并成一个更宽访问，也不为 MMIO 提供额外原子性。

源码：[amba 目录](../chipmunk/src/amba)。验证：

```shell
./mill chipmunk.test.testOnly \
  chipmunk.test.amba.AxiToAcornBridgeSpec \
  chipmunk.test.amba.AxiLiteToAxiBridgeSpec \
  chipmunk.test.amba.AcornToAxiBridgesSpec
```
