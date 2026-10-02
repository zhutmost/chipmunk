package chipmunk.test

import chisel3.*

import chipmunk.MuxPriority

private object MuxPrioritySpecDut {

  final class Harness(size: Int) extends Module {
    val io = IO(new Bundle {
      val select         = Input(UInt(size.W))
      val data           = Input(Vec(size, UInt(8.W)))
      val defaultValue   = Input(UInt(8.W))
      val withoutDefault = Output(Vec(3, UInt(8.W)))
      val withDefault    = Output(Vec(3, UInt(8.W)))
      val emptyPairs     = Output(UInt(8.W))
      val emptySelectors = Output(UInt(8.W))
    })

    private val selectors = io.select.asBools
    private val inputs    = io.data
    private val pairs     = selectors.zip(inputs)

    io.withoutDefault(0) := MuxPriority(pairs)
    io.withoutDefault(1) := MuxPriority(selectors, inputs)
    io.withoutDefault(2) := MuxPriority(io.select, inputs)

    io.withDefault(0) := MuxPriority(pairs, default = io.defaultValue)
    io.withDefault(1) := MuxPriority(selectors, inputs, default = io.defaultValue)
    io.withDefault(2) := MuxPriority(io.select, inputs, default = io.defaultValue)

    io.emptyPairs := MuxPriority(Seq.empty[(Bool, UInt)], default = io.defaultValue)

    io.emptySelectors := MuxPriority(Seq.empty[Bool], Seq.empty[UInt], default = io.defaultValue)
  }
}

class MuxPrioritySpec extends ChipmunkFlatSpec {

  private def check(dut: MuxPrioritySpecDut.Harness, data: Seq[Int], defaultValue: Int, mask: Int): Unit = {
    dut.io.select #= mask.U
    dut.io.defaultValue #= defaultValue.U

    for index <- data.indices do {
      dut.io.data(index) #= data(index).U
    }

    dut.clock.step(0)

    // Calculate expected values independently: smaller indices have higher priority.
    val selectedIndex = data.indices.find(index => ((mask >>> index) & 1) != 0)

    val expectedWithoutDefault =
      selectedIndex.map(index => data(index)).getOrElse(data.last)

    val expectedWithDefault =
      selectedIndex.map(index => data(index)).getOrElse(defaultValue)

    withClue(s"mask=$mask, data=$data, default=$defaultValue: ") {
      for overload <- 0 until 3 do {
        withClue(s"overload=$overload: ") {
          dut.io.withoutDefault(overload).expect(expectedWithoutDefault.U)
          dut.io.withDefault(overload).expect(expectedWithDefault.U)
        }
      }

      dut.io.emptyPairs.expect(defaultValue.U)
      dut.io.emptySelectors.expect(defaultValue.U)
    }
  }

  "MuxPriority" should "select the first asserted input across all overloads" in {
    simulate(new MuxPrioritySpecDut.Harness(size = 4)) { dut =>
      val patterns = Seq(
        (Seq(0x11, 0x22, 0x44, 0x88), 0xe7),
        (Seq(0xff, 0x80, 0x01, 0x00), 0x5a),
        (Seq(0x00, 0x00, 0x00, 0x00), 0xff),
      )

      for {
        (data, defaultValue) <- patterns
        mask                 <- 0 until 16
      } {
        check(dut, data, defaultValue, mask)
      }
    }
  }

  it should "support single inputs and explicit defaults for empty mappings" in {
    simulate(new MuxPrioritySpecDut.Harness(size = 1)) { dut =>
      val patterns = Seq((0x00, 0xff), (0x5a, 0xa5), (0xff, 0x00))

      for {
        (value, defaultValue) <- patterns
        mask                  <- 0 until 2
      } {
        check(dut, Seq(value), defaultValue, mask)
      }
    }
  }
}
