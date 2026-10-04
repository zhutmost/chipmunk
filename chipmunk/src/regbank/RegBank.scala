package chipmunk
package regbank

import chisel3.*
import chisel3.util.{Cat, Mux1H}

import chipmunk.acorn.{AcornIO, AcornParams}

/** Acorn register bank with independent one-entry read and write response buffers.
  *
  * Commands update fields and sample read data when accepted. Read side effects return the previous value; simultaneous
  * reads and writes also read the previous value. Responses stay stable until accepted.
  */
class RegBank(val config: RegBankConfig) extends Module {
  def this(addrWidth: Int, dataWidth: Int, regs: Seq[RegElementConfig]) =
    this(RegBankConfig(AcornParams(dataWidth, addrWidth), regs))

  val regsConfig: Seq[RegElementConfig] = config.regs
  val io                                = IO(new Bundle {
    val access = Slave(new AcornIO(config.params))
    val fields = Master(new RegBankFieldIO(config.regs))
  })

  private val wrPending = RegInit(false.B)
  private val rdPending = RegInit(false.B)
  private val wrError   = RegInit(false.B)
  private val rdError   = RegInit(false.B)
  private val rdData    = RegInit(0.U(config.params.dataWidth.W))

  io.access.wr.cmd.ready      := !wrPending || io.access.wr.rsp.ready
  io.access.rd.cmd.ready      := !rdPending || io.access.rd.rsp.ready
  io.access.wr.rsp.valid      := wrPending
  io.access.rd.rsp.valid      := rdPending
  io.access.wr.rsp.bits.error := wrError
  io.access.rd.rsp.bits.error := rdError
  io.access.rd.rsp.bits.data  := rdData

  private val wrHits    = config.regs.map(reg => io.access.wr.cmd.bits.addr === reg.addr.U)
  private val rdHits    = config.regs.map(reg => io.access.rd.cmd.bits.addr === reg.addr.U)
  private val wrBitMask = Cat(io.access.wr.cmd.bits.strobe.asBools.reverse.map(bit => Cat(Seq.fill(8)(bit))))
  private val readWords = config.regs.zipWithIndex.map { case (reg, index) =>
    val pieces = reg.fields.map { field =>
      val instance = Module(new RegField(field))
      instance.io.frontdoor.wrEnable  := io.access.wr.cmd.fire && wrHits(index)
      instance.io.frontdoor.rdEnable  := io.access.rd.cmd.fire && rdHits(index)
      instance.io.frontdoor.wrData    := io.access.wr.cmd.bits.data(field.endOffset, field.baseOffset)
      instance.io.frontdoor.wrBitMask := wrBitMask(field.endOffset, field.baseOffset)
      io.fields(s"${reg.name}_${field.name}") <> instance.io.backdoor
      (instance.io.frontdoor.rdData.pad(config.params.dataWidth) << field.baseOffset)(config.params.dataWidth - 1, 0)
    }
    pieces.reduce(_ | _)
  }

  when(io.access.wr.cmd.fire) {
    wrPending := true.B
    wrError   := !wrHits.reduce(_ || _)
  }.elsewhen(io.access.wr.rsp.fire) {
    wrPending := false.B
  }
  when(io.access.rd.cmd.fire) {
    rdPending := true.B
    rdError   := !rdHits.reduce(_ || _)
    rdData    := Mux(rdHits.reduce(_ || _), Mux1H(rdHits, readWords), 0.U)
  }.elsewhen(io.access.rd.rsp.fire) {
    rdPending := false.B
  }
}
