# 🐿️ Bits/Data 等的更多方法

`chipmunk` 为 Chisel 的原生类型 `Bits`/`Data` 等增加了若干额外的方法（主要是语法糖），为用户提供了一些更加便捷的编码选择，提高代码的可读性。

## `chisel3.Bits` 的额外方法

`chisel3.Bits` 是 Chisel 的 `UInt`/`SInt`/`Bool` 等类型的父类，因此以下方法可以被这些类型的实例调用。

这些方法是在 `chipmunk.AddMethodsToBits` 这个隐式类中实现的。

### `msBits`/`lsBits` — 获取信号的最高/低 n 比特

`chisel3.Bits` 已提供了类似的方法 `x.head(n)` 和 `x.tail(n)`，其中前者是获取该信号的高 n 比特，后者是去掉该信号的高 n 比特（相当于 `x.head(x.getWidth - n)`）。这两个方法显然是来自函数式编程语言处理队列的习惯（~~Haskell 用户狂喜~~），但和绝大多数硬件工程师的习惯很不一致，且导致代码可读性降低。硬件工程师习惯用“某某信号的高N比特/低N比特”来对多比特信号进行切片。

因此，Chipmunk 提供了 `x.msBits(n)` 和 `x.lsBits(n)` 两组方法，它们的功能正如方法名（most/least significant bits）：前者 `x.msBits(n)` 和 `x.head(n)` 行为类似，后者 `x.lsBits(n)` 则是获取该信号的低 n 比特。

具体如下：

- `def msBits(n: Int = 1): UInt`
返回当前信号实例的最高 n 比特。
- `def lsBits(n: Int = 1): UInt`
返回当前信号实例的最低 n 比特。
- `def msBit: Bool`
返回当前信号实例的最高 1 比特，注意返回类型为 `Bool`，与 `msBits(n)` 不同。
- `def lsBit: Bool`
返回当前信号实例的最高 1 比特，注意返回类型为 `Bool`，与 `lsBits(n)` 不同。

### `filledOnes`/`filledZeros`/`filledWith`/`assignOnes`/`assignZeros` — 将信号的所有比特赋 1 或 0

将一个信号的所有比特赋 1 或 0 是一个很普遍的需求，SystemVerilog 中引入了专门的语法：
```systemverilog
assign allOnes = '1;
assign allZeros = '0;
```
Chisel 中赋全 0 是容易实现的（`x := 0.U`），但赋全 1 缺乏优雅易读的实现方法。常见的写法包括：
```scala
x1 := Fill(x1.getWidth, b).asTypeOf(x1)
x2 := ~ 0.U // ~ 0.S if SInt
```
无论如何，都很不直观。特别是如果我希望将所有比特赋值为某个 `Bool` 信号或某个 Scala `Boolean` 变量输出时，代码会变得更加难以阅读。

因此，Chipmunk 提供了 `filledOnes`/`filledZeros`/`filledWith` 等一系列方法。具体如下：

- `def filledWith(b: Bool): T`
  返回一个和当前信号相同类型的信号，但该信号的所有比特都是为 `b`。
- `def filledWith(b: Boolean): T`
  返回一个和当前信号相同类型的信号，但该信号的所有比特都是为 b 的 `Bool` 字面量（即`b.B`）。
- `def filledOnes(): T`
  返回一个和当前信号相同类型的信号，但该信号的所有比特都是为 `true.B`。
- `def filledZeros(): T`
  返回一个和当前信号相同类型的信号，但该信号的所有比特都是为 `false.B`。

上述方法的共同特点是，它们会返回一个新的信号，不会改变当前信号的赋值。如果需要将一个信号的所有比特都赋值为全 0 或全 1，需要使用 `assignOnes`/`assignZeros`：

- `def assignOnes(): Unit`：
  将当前信号的所有比特都赋值为 `true.B`，相当于 `x := x.filledOnes()`。
- `def assignZeros(): Unit`：
  将当前信号的所有比特都赋值为 `false.B`，相当于 `x := x.filledZeros()`。

### `isOneHot` — 判断当前信号是否为独热码（one-hot）

如方法名的字面意思，判断当前信号是否为独热码。若是则输出 `true.B`，反之则输出 `false.B`。

## `chisel3.Data` 的额外方法

`chisel3.Data` 是 Chisel 的各硬件类型的父类。

这些方法是在 `chipmunk.AddMethodsToData` 这个隐式类中实现的。

### `dontTouch` — `dontTouch()` 的语法糖

`chisel3.dontTouch` 可以阻止 Chisel 对该信号进行优化，常用于保留某中间信号的命名、保留与模块输出无关的电路等。这是一个单例对象，下面是 Chisel 提供的一个用法等例子：
```scala
class MyModule extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val b = Output(UInt(32.W))
  })
  io.b := io.a
  val dead = RegNext(io.a +% 1.U) // normally dead would be pruned by DCE
  dontTouch(dead) // Marking it as such will preserve it
}
```

Chipmunk 提供了一个方法版本，可以直接调用 Data 类型的 dontTouch 方法实现同样的效果，为用户提供更多的编码选择。因此，上面的代码也可以写成下面这样，在有的时候这可以节省代码量。
```scala
class MyModule extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val b = Output(UInt(32.W))
  })
  io.b := io.a
  val dead = RegNext(io.a +% 1.U).dontTouch
}
```
