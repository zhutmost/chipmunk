# 🌰 Acorn Bus

Acorn 是用于模块内部地址访问的轻量接口，适合配置寄存器、SRAM 和多 bank 存储连接。
读写分别使用 Command 和 Response 通道，写地址、数据和字节掩码放在同一个 Command 中。
所有通道采用 Stream ready/valid 握手，在 `valid && ready` 时完成传输。

## 协议约定

- 地址以字节为单位，每条命令访问一个完整数据字，并要求自然对齐。
  例如，64 位接口的数据字起始地址为 `0x00、0x08、0x10…`。
- `valid` 生效后，发送方必须保持 `valid` 和整个 payload，直到握手完成。
  向下游发送的 valid 不依赖下游 ready 的组合值。
- 每个接受的命令恰好对应一个响应，包括错误访问。端点不能在没有对应命令时发起响应。
- 每个 master 的读响应按其读命令顺序返回，写响应按其写命令顺序返回。
  读写之间及不同 master 之间没有隐含的全局顺序。
- 写响应表示端点定义的本次写操作已完成。需要“先写后读”时，master 应收到写响应后再发读命令。
- `strobe(i)=1` 使能 `data(8*i+7, 8*i)`。全零 strobe 不修改数据，仍对应一个写响应。
- `error=false` 表示成功，`error=true` 表示失败。Crossbar 的错误端点返回零读数据。

### 通道和字段

| 通道 | Payload 字段 | master 侧 payload / valid 方向 |
| --- | --- | --- |
| `rd.cmd` | `addr` | 输出 |
| `rd.rsp` | `data, error` | 输入 |
| `wr.cmd` | `addr, data, strobe` | 输出 |
| `wr.rsp` | `error` | 输入 |

每个通道的 ready 方向与其 valid 方向相反。数据宽度为不小于 8 的二次幂，strobe 宽度为数据字节数。

## 组件

使用 `import chipmunk.acorn.*`。源码在 `chipmunk/src/acorn/`，测试在
`chipmunk/test/src/acorn/`，测试包名为 `chipmunk.test.acorn`。

| 文件 | 职责 |
| --- | --- |
| `AcornIO.scala` | IO 和读写命令、响应 payload |
| `AcornConfig.scala` | 参数、容量、仲裁方式和静态地址映射配置 |
| `AcornRouting.scala` | 内部共用的来源/目标 FIFO 及 Stream 路由逻辑 |
| `AcornMux.scala` | 多 master 到一个 slave 的命令仲裁和响应分发 |
| `AcornDemux.scala` | 一个 master 到多个 slave 的命令分发和响应排序 |
| `AcornCrossbar.scala` | 每 master 一个 Demux、每 slave 一个 Mux，并完成地址转换 |
| `AcornErrorPoint.scala` | 可独立实例化的错误响应端点 |

Mux 的来源 FIFO 保存命令由哪个 master 发出；Demux 的目标 FIFO 保存命令发往哪个 slave。
它们在命令握手时记录路由，在相应响应握手时移除记录。不同 slave 独立仲裁，可以同时接受不同 master 的访问。

## Crossbar 的配置

| 配置 | 含义 |
| --- | --- |
| `params` | 公共数据宽度及 master 侧字节地址宽度；数据宽度为不小于 8 的二次幂 |
| `numMasters` | master 数量 |
| `slaves` | 按输出端口顺序排列的 slave 配置；名字必须唯一，地址窗口不能重叠 |
| `name / base / size` | slave 名字和全局地址区间 `[base, base + size)` |
| `localBase` | slave 看到的地址是 `globalAddr - base + localBase`，默认从零开始 |
| `localAddrWidth` | 可选的 slave 地址宽度；默认由本地地址窗口自动推导 |
| `readable / writable` | slave 支持的访问方向，默认均支持 |
| `masterOutstanding` | 每个 master 每方向的总在途容量，跨所有目标统计 |
| `slaveOutstanding` | 每个 slave 每方向的总在途容量，跨所有 master 统计 |
| `arbitration` | RoundRobin 默认，或 LowerFirst 固定低索引优先 |
| `connections` | 可选的每 master 可达 slave 名字集合；None 表示全部连接 |

全局窗口、本地基址以及窗口大小都必须按一个数据字对齐。窗口覆盖范围必须同时适配全局和本地地址宽度。
一个 slave 当前对应一个连续窗口。交错 bank、多个窗口别名以及数据宽度转换留给独立映射或适配组件。

```scala
import chisel3.*
import chipmunk.acorn.*

val bus = AcornParams(dataWidth = 64, addrWidth = 32)
val config = AcornCrossbarConfig(
  params = bus,
  numMasters = 2,
  slaves = Seq(
    AcornSlaveConfig("bank0", base = BigInt("10000000", 16), size = 0x10000),
    AcornSlaveConfig("bank1", base = BigInt("10010000", 16), size = 0x10000),
    AcornSlaveConfig("csr", base = BigInt("20000000", 16), size = 0x1000)
  ),
  masterOutstanding = AcornOutstanding(read = 4, write = 4),
  slaveOutstanding = AcornOutstanding(read = 8, write = 4),
  arbitration = AcornArbitration.RoundRobin,
  connections = Some(Seq(
    Set("bank0", "bank1", "csr"), // master 0
    Set("bank0", "bank1")         // master 1
  ))
)
val crossbar = Module(new AcornCrossbar(config))

// crossbar.io.ins(0) <> cpu.io.acorn
// crossbar.io.ins(1) <> dma.io.acorn
// crossbar.slave("bank0") <> bank0.io.access
// crossbar.slave("bank1") <> bank1.io.access
// crossbar.slave("csr") <> registers.io.access
```

本例三个 slave 的地址宽度分别推导为 16、16、12 位；slave 端口是 MixedVec，允许不同地址宽度。
数据宽度始终为 64 位。如果端点有固定地址宽度，设置 `localAddrWidth = Some(width)`。
设置 `localBase = base` 可让端点继续看到全局地址，相应本地地址宽度必须足够。

## 单独使用 Mux 和 Demux

```scala
val mux = Module(new AcornMux(
  params = AcornParams(64, 16),
  numMasters = 2,
  outstanding = AcornOutstanding(8, 4)
))
// mux.io.ins(0) <> master0
// mux.io.ins(1) <> master1
// slave <> mux.io.out

val demux = Module(new AcornDemux(
  params = AcornParams(64, 32),
  numSlaves = 4,
  outstanding = AcornOutstanding(4, 4)
))
demux.io.rdSelect := demux.io.in.rd.cmd.bits.addr(17, 16)
demux.io.wrSelect := demux.io.in.wr.cmd.bits.addr(17, 16)
// demux.io.in <> master
// 各端点连接 demux.io.outs；Demux 本身保留原地址，不做地址转换。
```

裸 Demux 的无效 selector 会阻塞命令；Crossbar 自动将未映射、未对齐、方向不允许或连接不允许的命令送到
该 master 私有的错误端点，因此这些情况仍产生恰好一个按序错误响应。

## 单独使用错误端点

```scala
val errorPoint = Module(new AcornErrorPoint(
  AcornParams(64, 32), AcornOutstanding(read = 4, write = 4)
))
// errorPoint.io.access <> master
```

每个已接受的读写命令都会产生一个 `error=true` 的响应，读响应的数据为零。

## 时序和组合约束

- 每个 master 的读响应按其读命令顺序返回，写响应按写命令顺序返回；读写之间和不同 master 之间没有全局顺序。
- 每个 slave 必须按各方向的命令接受顺序返回响应；每个已接受的命令恰好有一个响应。
- valid 生效后，发送方在握手前必须保持 valid 和 payload。写 strobe 为字节使能；全零 strobe 仍产生写响应。
- Mux 仲裁器只在命令阻塞期间锁定选择；命令接受后可以仲裁其他 master，响应归属由 FIFO 保存。
- 命令可组合通过路由器；路由记录是寄存的。因此即使端点立即提供响应，首次向上游返回也要等路由记录可见。
  端点不能等待这次响应握手才接受命令，响应必须能保持到 ready。
- 记录 FIFO 使用 `pipe=false, flow=false`，满时不会利用同周期响应释放的空间。深度 1 会限制流水吞吐；
  按端点延迟增大深度可以提高吞吐，首版默认深度 4。
- 在 Crossbar 中，master 接受、slave 接受和两层路由记录是同一次命令握手。不要在 Demux 和 Mux 之间任意加入
  独立接收命令的 Queue；不同目标上的命令发出顺序变化可能形成循环响应等待。
- 响应背压和跨目标按序返回会造成队首阻塞。首版不提供 master 之间的完全阻塞隔离。
- 所有组件工作于同一时钟域，连接端点应一致复位；复位取消在途事务。跨时钟桥后续单独设计。

Mux 的在途上限是所有 master 的合计，Demux 的上限是一个 master 跨所有目标的合计。
单端口 SRAM 的读写仲裁、同地址读写冲突、寄存器字段副作用等，由端点负责。
首版不包含 burst、线上 ID、乱序返回或原子读改写操作。

## 验证

测试覆盖来源归属、阻塞时锁定、跨目标按序返回、非法 selector、非零本地基址进位、全地址空间窗口、
深度 1/4 的多 master 并行读写与随机命令/响应背压，以及配置和访问错误。
使用项目现有的 `ChipmunkFlatSpec`、`#=` 和 `expect`。

```bash
mill chipmunk.test.testOnly \
  chipmunk.test.acorn.AcornConfigSpec \
  chipmunk.test.acorn.AcornMuxSpec \
  chipmunk.test.acorn.AcornDemuxSpec \
  chipmunk.test.acorn.AcornCrossbarSpec
```
