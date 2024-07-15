# 🌰 Acorn Bus

Acorn Bus 是 Chipmunk 预置的一款片上总线，它旨在提供一款简单但高效的片上总线的硬件实现。相比 AMBA AXI 等成熟的片上总线协议，Acorn Bus 简化了许多功能，更加易于硬件实现。

Acorn Bus 的信号定义分为两部分：Command 通道和 Response 通道。Command 通道用于 Master 向 Slave 发送请求，Response 通道用于 Slave 向 Master 返回响应。两个通道都采用 ready-valid 握手协议来解耦，即当且仅当该通道的 ready 和 valid 信号都有效时完成一次传输。

```mermaid
sequenceDiagram
    participant M as Master
    participant S as Slave
    M ->> S: Command 0
    S -->> M: Response 0
    M ->> S: Command 1
    M ->> S: Command 2
    S -->> M: Response 1
    S -->> M: Response 2
```

Acorn Bus 有两种变体：Sp (Single Port) 和 Dp (Double Port)。主要的区别是，Acorn Sp Bus 是读写共用 Command 和 Response 通道的，而 Acorn Dp Bus 是读写分离的，即读和写有各自独立的 Command 和 Response 通道。

## Acorn Sp Bus

### 信号定义

#### Command Channel (`cmd`)

| Signal   | Direction | Bit Width      | Description                                                                            |
|----------|-----------|----------------|----------------------------------------------------------------------------------------|
| `read`   | Output    | 1              | Flag bit indicating the current command is a read or write request. (1: read, 0：write) |
| `addr`   | Output    | ADDR_WIDTH     | The address to be written or read.                                                     |
| `wdata`  | Output    | DATA_WIDTH     | The data to be written. (valid only when `read` is 0)                                  |
| `wmask`  | Output    | DATA_WIDTH / 8 | The byte mask of `wdata`. (valid only when `read` is 0)                                |
| `valid`  | Output    | 1              | The valid signal of the command channel.                                               |
| `ready`  | Input     | 1              | The ready signal of the command channel.                                               |

以上信号方向（Direction）是对于 Master 侧而言的，对于 Slave 侧而言则相反（下同）。

其中，写数据掩码信号 `wmask` 用于指示写数据的有效字节，其行为和 AMBA AXI 的 `strb` 信号类似。当且仅当 `wmask` 的某一位为 1 时，对应的 `wdata` 的对应字节会被写入。 例如，当 `wmask` 的第 0 bit 为低时，`wdata` 的最低字节（最低 8 bit）有效；当 `wmask` 的第 1 bit 为高时，`wdata` 的第 8 ~ 15 位有效，以此类推。

#### Response Channel (`resp`)

| Signal  | Direction | Bit Width  | Description                                                       |
|---------|-----------|------------|-------------------------------------------------------------------|
| `rdata` | Output    | DATA_WIDTH | The readout data. (valid only when the corresponding `read` is 1) |
| `error` | Output    | 1          | Whether the operation is successfully done.                       |
| `valid` | Output    | 1          | The valid signal of the response channel.                         |
| `ready` | Input     | 1          | The ready signal of the response channel.                         |

### 传输时序

如下图所示，Master 先后完成了：
1. 写入数据 `0xA` 到地址 `0x0`；
2. 读取地址 `0x1` 的数据，返回 `0xB`；
3. 读取地址 `0x2` 的数据，返回 `0xC`。

![acorn-transaction-waveform](./assets/acorn-transaction-waveform.jpg)

为了简洁，图中未绘出 `wmask` 信号和 `status` 信号。

传输过程中一些关键的要点：
- Command 和 Response 通道均要求发起侧不撤回（irrevocable），即 `valid` 一旦生效直到 `ready` 生效前其他信号不可发生改变，`valid` 也不能拉低。
- 和其他的 ready-valid 握手协议一样，Command 和 Response 通道的 `valid` 不能依赖于 `ready`，以避免组合逻辑环。
- 在没有从 Command 通道收到有效请求时，Slave 不可以发起 Response 通道的传输请求，即不得拉高 `valid`。

## Acorn Dp Bus

Acorn Dp Bus 和 Sp 的主要区别是读写分离，因此除了信号定义之外的部分不再赘述。

### 信号定义

#### Write Command Channel (`wr.cmd`)

| Signal  | Direction | Bit Width      | Description                              |
|---------|-----------|----------------|------------------------------------------|
| `addr`  | Output    | ADDR_WIDTH     | The address to be written.               |
| `wdata` | Output    | DATA_WIDTH     | The data to be written.                  |
| `wmask` | Output    | DATA_WIDTH / 8 | The byte mask of `wdata`.                |
| `valid` | Output    | 1              | The valid signal of the command channel. |
| `ready` | Input     | 1              | The ready signal of the command channel. |

#### Write Response Channel (`wr.resp`)

| Signal  | Direction | Bit Width | Description                                 |
|---------|-----------|-----------|---------------------------------------------|
| `error` | Output    | 1         | Whether the operation is successfully done. |
| `valid` | Output    | 1         | The valid signal of the response channel.   |
| `ready` | Input     | 1         | The ready signal of the response channel.   |

#### Read Command Channel (`rd.cmd`)

| Signal  | Direction | Bit Width  | Description                              |
|---------|-----------|------------|------------------------------------------|
| `addr`  | Output    | ADDR_WIDTH | The address to be read.                  |
| `valid` | Output    | 1          | The valid signal of the command channel. |
| `ready` | Input     | 1          | The ready signal of the command channel. |

#### Read Response Channel (`rd.resp`)

| Signal  | Direction | Bit Width  | Description                                 |
|---------|-----------|------------|---------------------------------------------|
| `rdata` | Output    | DATA_WIDTH | The readout data.                           |
| `error` | Output    | 1          | Whether the operation is successfully done. |
| `valid` | Output    | 1          | The valid signal of the response channel.   |
| `ready` | Input     | 1          | The ready signal of the response channel.   |

## 在 Chipmunk 中的实现

Acorn Bus 的 IO 定义：
- Acorn Sp Bus：`chipmunk.acorn.AcornSpIO`
- Acorn Dp Bus：`chipmunk.acorn.AcornDpIO`

Chipmunk 提供了 Sp 和 Dp 之间的相互转换：
- `chipmunk.acorn.AcornSp2DpBridge`
- `chipmunk.acorn.AcornDp2SpBridge`

此外，Chipmunk 还提供了 Acorn Bus 与 AXI、AXI-Lite 等其他总线的转换桥接模块。

## 与其他总线的差异

### Hummingbird ICB

Acorn Bus 在设计上参考了[蜂鸟 E203](https://github.com/riscv-mcu/e203_hbirdv2) 项目中的 ICB（Internal Chip Bus）总线。主要区别包括：
- Acorn Bus 允许用户定义数据信号和地址地址的位宽，而不是固定的 32 bit。
- Acorn Bus 提供了 Sp 和 Dp 两个变体，其中 Sp 变体与 ICB 的定义类似，但 Dp 变体的读写是可以并行进行的。

### AMBA AXI & AHB

Acorn Bus 与 AMBA AXI、AHB 相比，在功能上有所简化。主要区别如下：
- Acorn Bus 的 Dp 变体和 AXI 一样，同样实现了读写分离；Acorn Bus 的 Sp 变体和 AHB 一样，读写共用一组 Command 与 Response 通道。
- Acorn Bus 支持多周期连续传输，但 Command 通道的每个有效握手周期 Master 都需要给出一个地址。
- Acorn Bus 不支持乱序返回，即 Slave 必须按照 Master 的请求顺序返回数据。
- Acorn Bus 和 AXI 一样，都支持 Write Byte Mask、Outstanding Transaction 等特性。

简单（但不准确）地说，用户可以将 Acorn Dp Bus 当作一个 AW 和 W 通道合并且缺少 `id`、`burst`、`prot` 等控制信号（和他们对应的高级特性）的 AXI 总线。

### SRAM Interface

尽管 SRAM 并不是一种总线，但 Acorn Bus 和 SRAM 的接口定义有许多相似之处，其 Sp 和 Dp 变体分别对应单口（single-port）和双口（double-port）SRAM 的读写接口。因此，在某种意义上，用户可以将 Acorn Bus 想像成是一种请求和响应都带 ready-valid 握手的特殊 SRAM 接口。
