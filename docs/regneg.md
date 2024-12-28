# 🐿️ 下降沿触发寄存器 RegNeg

`chipmunk` 提供了在时钟负沿触发的寄存器 `RegNegNext` 和 `RegNegEnable`。它们具有和`chisel3.RegNext`、`chisel3.util.RegEnable` 类似的接口，唯一的区别是其在时钟的下降沿（而不是上升沿）完成数据锁存。

## Why？

Chisel 已经提供了一系列的时钟上升沿触发的寄存器，包括 `RegInit`、`RegNext`、`RegEnable` 等。有些场合下我们需要生成一些时钟下降沿采样的寄存器，但现阶段在 Chisel 中我们只能采用 BlackBox 的方法实现。

关于 Chisel 是否应该添加对时钟下降沿触发寄存器的原生支持，社区在 FIRRTL 时代就进行过一些讨论（如 [#695](https://github.com/chipsalliance/firrtl/issues/695)）。尽管 Chisel 现在的后端 CIRCT Firtool 是支持生成 `negedge clock` 电路的，但目前 Chisel 前端还没有对应的结构。

## Usage

`chipmunk` 提供了四种不同签名的下降沿触发寄存器：
```scala
RegNegNext(next: T)
RegNegNext(next: T, init: T, isResetAsync: Boolean = true)
RegNegEnable(next: T, enable: Bool)
RegNegEnable(next: T, init: T, enable: Bool, isResetAsync: Boolean = true)
```

它们的参数功能如下：
- `next`：待锁存的输入数据，会在时钟的下降沿被采样。
- `init`：复位初始数据；需要上下文中存在复位信号。
- `enable`：锁存使能信号，当它为`true.B`时`next`会被锁存。
- `isResetAsync`：复位信号是否为异步复位（`AsyncReset`）；默认为`true`。

用户不需要在参数列表中给定时钟和复位信号，和 Chisel 的 `RegXXX` 一样，它们会根据上下文使用对应的时钟和复位信号。

唯一需要注意的是参数`isResetAsync`。由于 Chisel 的类型系统限制，同步复位和异步复位的类型都是`ResetType`（除非显式声明为`AsyncReset()`，但这种情况不常见），Chisel 会在 Elaboration 阶段再推导决定它们属于哪种类型的复位信号并赋予对应的类型：`Bool` 或 `AsyncReset`。Chisel 没有提供公开 API 判断复位类型，因此需要用户通过设置 `isResetAsync` 告诉 `RegNeg` 最终使用哪种复位。
<!-- 如果用户的设置与实际 Chisel 推导出的复位类型不符，后端代码生成阶段 Firtool 会报错。 -->

下面给出一些实际用例供参考。
```scala
val nextVal0 = Wire(Vec(8, UInt(3.W)))
val nextVal1 = Wire(SInt(3.W))
val enable1 = Wire(Bool())

withClockAndReset(clock, reset) {
  val r0 = RegNegNext(io.nextVal0, init = VecInit(Seq.fill(8)(1.U(3.W))))
  val r1 = RegNegEnable(io.nextVal1, -3.S, io.enable1)
}
```
