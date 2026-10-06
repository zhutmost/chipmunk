# 🔍 SPI Debugger

SPI Debugger 允许用户通过 SPI 接口访问片上总线，从而调试 SoC 的各种外设。

`chipmunk.spi.SpiDebugger` 的 `sSpi` 是 SPI Slave 接口，`mDbg` 是 32 位 Acorn Master 接口，可以经桥接连接到 AXI 总线。SPI 使用 MSB first，`cpol`、`cpha` 参数选择四种标准模式。

Acorn 地址以字节为单位，要求 4 字节对齐；`busAddrWidth` 支持 2～64 位。SPI 命令里的 6 位寄存器索引保持不变，内部 RegBank 字节地址为 `index * 4`。

## Register Map

SPI Debugger 内部寄存器定义如下：

### `BUS_ADDR_H` (0x01)

该寄存器仅当配置选项 `busAddrWidth` > 32 时才会存在，且只有其低 (`busAddrWidth`-32) bit 可被访问。

其值与寄存器 `BUS_ADDR_L` 组合在一起作为总线访问地址（`wr/rd.cmd.bits.addr`）。

| Field        | Bit width | Bit slice | Access |
|--------------|-----------|-----------|--------|
| `BUS_ADDR_H` | 13        | 12:0      | R/W    |

表中的例子是 `busAddrWidth` = 45 时的情况。

### `BUS_ADDR_L` (0x00)

当配置选项 `busAddrWidth` > 32 时，寄存器 `BUS_ADDR_L` 的值作为低 32 位，与 `BUS_ADDR_H` 的值组合在一起作为总线访问地址；否则，该寄存器的低 `busAddrWidth` bit 作为总线访问地址（`wr/rd.cmd.bits.addr`）。

| Field        | Bit width | Bit slice | Access |
|--------------|-----------|-----------|--------|
| `BUS_ADDR_L` | 32        | 31:0      | R/W    |

表中为 `busAddrWidth >= 32` 的情况；更窄的地址只实现低 `busAddrWidth` 位，其余位读为零。

### `BUS_WR_RESP` (`0x02`)

读取该寄存器，其最低 bit 表示上一次总线写操作的错误状态（`wr.rsp.bits.error`），0 表示成功，1 表示有错误发生。

| Field         | Bit width | Bit slice | Access |
|---------------|-----------|-----------|--------|
| `BUS_WR_RESP` | 1         | 0:0       | RO     |

写入该寄存器（无论写 1 或者 0），会尝试发起一次总线写操作；BUSY 时拒绝，地址错误在本地完成。

### `BUS_RD_RESP` (`0x03`)

读取该寄存器，其最低 bit 表示上一次总线读操作的错误状态（`rd.rsp.bits.error`），0 表示成功，1 表示有错误发生。

| Field         | Bit width | Bit slice | Access |
|---------------|-----------|-----------|--------|
| `BUS_RD_RESP` | 1         | 0:0       | RO     |

写入该寄存器（无论写 1 或者 0），会尝试发起一次总线读操作；BUSY 时拒绝，地址错误在本地完成。

### `BUS_WR_DATA` (`0x04`)

该寄存器的值作为总线写操作的数据（`wr.cmd.bits.data`）。

| Field         | Bit width | Bit slice | Access |
|---------------|-----------|-----------|--------|
| `BUS_WR_DATA` | 32        | 31:0      | R/W    |

### `BUS_RD_DATA` (`0x05`)

读取该寄存器，将返回上一次总线读操作的数据（`rd.rsp.bits.data`）。发生读错误时保存零值。

| Field         | Bit width | Bit slice | Access |
|---------------|-----------|-----------|--------|
| `BUS_RD_DATA` | 32        | 31:0      | R/W    |

### `BUS_WR_MASK` (`0x06`)

该寄存器的值作为总线写操作的数据掩码（`wr.cmd.bits.strobe`），复位值为 `0xf`。零掩码仍发起总线写请求。

| Field         | Bit width | Bit slice | Access |
|---------------|-----------|-----------|--------|
| `BUS_WR_MASK` | 4         | 3:0       | R/W    |

### `STATUS` (`0x07`)

| Field | Bit | Access | Meaning |
|---|---:|---|---|
| BUSY | 0 | RO | 请求已锁存，命令或响应尚未完成 |
| RD_DONE | 1 | RO | 最近获准的读操作已经完成，包括本地地址检查失败 |
| WR_DONE | 2 | RO | 最近获准的写操作已经完成，包括本地地址检查失败 |
| REJECTED | 3 | RO | 忙时收到新触发，该触发被拒绝 |
| EARLY_READ | 4 | RO | 快速读的数据阶段开始时，本次结果尚不可用 |

每次新请求获准时清除 DONE、REJECTED 和 EARLY_READ，之后相应标志保持到下一次获准请求或复位；读取 STATUS 不清除标志。忙时被拒绝的请求不替换正在执行的请求，也不修改其返回结果。

读写共用一个未完成事务槽，请求获准时锁存地址、数据和 strobe。之后可以改写暂存寄存器，正在等待握手的 Acorn 命令保持不变。RESP 中的错误位在操作完成时更新，不能单独用它判断是否完成。

任意总线延迟下的读流程：等待 BUSY 清零，写入地址，写 BUS_RD_RESP 触发，检查 REJECTED 并轮询 RD_DONE，最后读取 BUS_RD_RESP 和 BUS_RD_DATA。写操作对应轮询 WR_DONE。

地址不对齐或超出配置宽度时，不向 Acorn 发命令，在本地设置 DONE 和对应 RESP 错误位。窄地址寄存器中未实现的高位读为零，但写入的溢出信息会保留，直到重写对应地址部分；不会静默访问截断后的地址。

### `TEST` (`0x3F`)

该寄存器用于 SPI 的 Loopback 读写测试，复位值为 `0x3f1b00e5`，其值对总线不会有任何影响。

| Field  | Bit width | Bit slice | Access |
|--------|-----------|-----------|--------|
| `TEST` | 32        | 31:0      | R/W    |

## SPI Command Sequences

要通过 SPI Debugger 访问其内部寄存器和总线侧 Acorn Bus 接口，Master 侧的 SPI Controller 应当：
1. 拉低 SSN 启动 SPI 传输；
2. 发送 1 byte 的 Command；
3. 根据不同的 Command，发送或接收若干字节的数据；
4. 拉高 SSN 结束 SPI 传输。

SPI Debugger 支持的 Command 包括：

- `REG_WR` (`0b00ii_iiii`)：写内部寄存器，低 6 bit 用于区分寄存器；
- `REG_RD` (`0b01ii_iiii`)：读内部寄存器，低 6 bit 用于区分寄存器；
- `BUS_WR` (`0b1000_0000`)：写总线；
- `BUS_RD` (`0b1100_0000`)：读总线；
- `NOP`：最高 bit 为 1 且不是 `0x80` / `0xC0` 的其他编码；其中 `i` 表示寄存器索引 bit。

### `REG_WR`

其传输序列为：`Command` (8b) + `WrData` (32b)

在 `WrData` 阶段，Master 需要将 32 bit 的待写数据通过 MOSI 线发送给 SPI Debugger。SPI Debugger 在接收到 32 bit 数据后，将其写入对应的寄存器。

### `REG_RD`

其传输序列为：`Command` (8b) + `RdData` (32b)

在 `RdData` 阶段，SPI Debugger 将 32 bit 的寄存器读出数据通过 MISO 线发送给 SPI Master。

### `BUS_WR`

其传输序列为：`Command` (8b) + `Addr` (32b) + `WrData` (32b)

在 `Addr` 和 `WrData` 阶段，Master 需要分别将 32 bit 的待写地址和待写数据发送给 SPI Debugger。SPI Debugger 会将它们填入寄存器 `BUS_ADDR_L` 和 `BUS_WR_DATA`，然后尝试发起一次总线写操作；忙时拒绝，地址不合法时在本地报告错误。

### `BUS_RD`

其传输序列为：`Command` (8b) + `Addr` (32b) + `Dummy` (8b) + `RdData` (32b)

在 `Addr` 阶段，Master 发送 32 bit 的地址低位。SPI Debugger 更新 `BUS_ADDR_L`，结合可选的 `BUS_ADDR_H` 尝试发起一次总线读操作；忙时拒绝，地址不合法时在本地报告错误。

收齐 8 bit Dummy 时锁存本次结果，整字发送期间不再改变。如果本次结果尚未到达，或者请求因忙被拒绝，则发送 `0xDEAD_BEEF` 并设置 EARLY_READ。迟到的响应仍更新 BUS_RD_DATA 和 RD_DONE，可随后通过寄存器读取；不会返回上一次事务的数据。

`0xDEAD_BEEF` 也可能是合法的总线数据，应通过 STATUS 判断。固定 Dummy 时间不保证任意总线响应延迟，推荐使用上面的轮询读流程。

### `NOP`

其传输序列为：`Command` (8b)

空操作，什么都不会发生。

## Common Issues

1. 即使需要连续多次访问，期间也必须先拉高 SSN 结束当前 SPI 传输，再拉低 SSN 开始下一次传输；
2. 在拉高 SSN 结束当前 SPI 传输前，请务必确定已经完成了整个操作序列所需的数据传输（即等待足够的时间）；
3. 系统时钟频率至少为 SCK 的 8 倍，假定 SCK 占空比接近 50%，且 IO 建立、保持及传播延迟有足够余量。占空比偏离时需按较短的半周期重新评估。这是主机使用条件，RTL 不强制检查外部时序；SSN 必须在首个采样边沿前被同步逻辑观察到，并保持到最后一个采样边沿之后。
4. REG_WR 的数据未收齐不写目标寄存器，BUS_WR 的数据未收齐不发总线写请求；快速命令收齐地址字后仍会更新 BUS_ADDR_L。总线读在地址收齐后发起，此后撤销 SSN 不取消请求。完整命令后的多余 SCK 被忽略，下一条命令需重新拉高、拉低 SSN。
5. 复位会取消本模块的未完成事务，必须同步复位相关 Acorn 端点。片选撤销不会取消事务；模块没有总线超时取消机制。
6. `hasMisoValid` 为真时，misoValid 直接由原始 SSN 限定，供 IO-cell 控制输出使能；实际三态由 IO-cell 实现。


## 接口与验证

四线接口 [SpiIO.scala](../chipmunk/src/spi/SpiIO.scala) 的默认方向为 master，调试器通过 `Slave(new SpiIO(...))` 使用；`hasMisoValid` 仅用于 slave 侧 IO-cell 控制。总线接入方式见 [Acorn](acorn.md) 和 [AMBA 桥接](amba.md)。

```shell
./mill chipmunk.test.testOnly chipmunk.test.spi.SpiDebuggerSpec
```

测试覆盖四种 SPI 模式、寄存器和快速访问、未完成帧、总线背压/延迟、忙时拒绝、EARLY_READ、地址错误和响应快照。
