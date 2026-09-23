package chipmunk

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{Fill, PopCount}

extension [T <: Bits](c: T) {

  /** Returns the `n` most significant bits as a UInt. */
  def msBits(n: Int = 1)(using SourceInfo): UInt =
    c.head(n)

  /** Returns the `n` least significant bits as a UInt. */
  def lsBits(n: Int = 1)(using SourceInfo): UInt =
    c.take(n)

  /** Returns the most significant bit as a Bool. */
  def msBit(using SourceInfo): Bool =
    c.head(1).asBool

  /** Returns the least significant bit as a Bool. */
  def lsBit(using SourceInfo): Bool =
    c(0)

  /** Returns the same type filled with every bit driven by `b`.
    *
    * `b` may be a hardware signal; it need not be a literal.
    */
  def filledWith(b: Bool)(using SourceInfo): T =
    Fill(c.getWidth, b).asTypeOf(c)

  /** Returns the same type filled with a constant Scala Boolean. */
  def filledWith(b: Boolean)(using SourceInfo): T =
    c.filledWith(b.B)

  /** Returns the same type with every bit set to one. */
  def filledOnes(using SourceInfo): T =
    c.filledWith(true.B)

  /** Returns the same type with every bit set to zero. */
  def filledZeros(using SourceInfo): T =
    c.filledWith(false.B)

  /** Drives this signal with ones. */
  def assignOnes()(using SourceInfo): Unit =
    c := c.filledOnes

  /** Drives this signal with zeros. */
  def assignZeros()(using SourceInfo): Unit =
    c := c.filledZeros

  /** Returns true exactly when one bit of this signal is set. */
  def isOneHot(using SourceInfo): Bool =
    PopCount.equalTo(1, c.asUInt)
}

extension [T <: Data](c: T) {

  /** Marks bound hardware with Chisel's dontTouch annotation and returns the same value for chaining. Same as
    * `chisel3.dontTouch(x)`.
    */
  def dontTouch: T =
    chisel3.dontTouch(c)
}
