package chipmunk
package regbank

import stream._

import chisel3._
import chisel3.util._

import scala.math.ceil

/** Configuration of of a register element in [[RegBank]].
  *
  * @param name
  *   Name of the register element. It should be unique in a [[RegBank]]. Only alphanumeric and underscore are allowed.
  * @param addr
  *   Address of the register element.
  * @param fields
  *   The configuration of register fields in the register element. A register element should have at least one field.
  */
case class RegElementConfig(name: String, addr: BigInt, fields: Seq[RegFieldConfig]) {
  require(fields.nonEmpty, "RegElement should have at least one field")
  require(name matches "[\\w]+", s"Register element name `$name` is invalid")

  if (fields.length >= 2) {
    require(
      fields.sortBy(_.baseOffset).combinations(2).forall(x => x.head.endOffset < x.last.baseOffset),
      "Bit range overlap is not allowed between two fields"
    )
  }
}

object RegElementConfig {

  /** Create a register element configuration with only one field.
    *
    * It has only one field with the name "*".
    *
    * @see
    *   [[RegElementConfig]]
    */
  def apply(
    name: String,
    addr: BigInt,
    bitCount: Int,
    initValue: UInt = 0.U,
    accessType: RegFieldAccessType = RegFieldAccessType.ReadWrite,
    collisionMode: RegFieldCollisionMode.Value = RegFieldCollisionMode.BackdoorWriteRead,
    backdoorUpdate: Boolean = false
  ): RegElementConfig = {
    RegElementConfig(
      name = name,
      addr = addr,
      Seq(RegFieldConfig("UNNAMED", 0, bitCount, initValue, accessType, collisionMode, backdoorUpdate))
    )
  }
}

/** Memory-mapped access interface of [[RegBank]].
  *
  * @note
  *   The mask bits (`wr.req.bits.mask`) are high-active. Only the data bits corresponding to mask bits of 1 will be
  *   written to the register fields. That is to say, if all bits in `wr.req.bits.mask` is 0, no data will be written
  *   during a write request.
  *
  * @param addrWidth
  *   Bit width of write/read address. Default is 32.
  * @param dataWidth
  *   Bit width of write/read data. Default is 32.
  * @param maskUnit
  *   The mask granularity for writing. In general, it should be a power of 2. Keep it 0 to generate no mask signal. For
  *   example, if `maskUnit` is 8, each mask bit corresponds to one byte of the data bits.
  */
class RegBankAccessIO(val addrWidth: Int = 32, val dataWidth: Int = 32, val maskUnit: Int = 0)
    extends Bundle
    with IsMasterSlave {
  val hasMask        = maskUnit > 0
  val maskWidth: Int = if (hasMask) ceil(dataWidth.toDouble / maskUnit).toInt else 0
  val wr = new Bundle {
    val req = Master(Flow(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(dataWidth.W)
      val mask = if (hasMask) Some(UInt(maskWidth.W)) else None
    }))
    val resp = Slave(Flow(new Bundle {
      val status = Bool()
    }))
  }
  val rd = new Bundle {
    val req = Master(Flow(new Bundle {
      val addr = UInt(addrWidth.W)
    }))
    val resp = Slave(Flow(new Bundle {
      val data   = UInt(dataWidth.W)
      val status = Bool()
    }))
  }

  def isMaster = true
}

/** Backdoor access interface of [[RegBank]].
  *
  * Use "ElementName_FieldName" to access a register field. For example, "STATUS_VERSION" means the field "VERSION" in
  * "STATUS". If the element has only one field, "ElementName" also can be used to access this field.
  *
  * @param regsConfig
  *   The configuration of register elements in [[RegBank]].
  */
class RegBankFieldIO(regsConfig: Seq[RegElementConfig])
    extends MapBundle[RegFieldBackdoorIO](
      regsConfig.flatMap(elemConfig =>
        elemConfig.fields.map(fieldConfig => {
          s"${elemConfig.name}_${fieldConfig.name}" -> Master(new RegFieldBackdoorIO(fieldConfig))
        })
      ): _*
    )
    with IsMasterSlave {
  override def apply(key: String): RegFieldBackdoorIO = {
    if (elements contains key) {
      elements(key)
    } else {
      // If the element has only one field, the field name can be omitted.
      val existsFieldName: Option[String] = regsConfig.collectFirst {
        case elem if elem.name == key && elem.fields.length == 1 => elem.fields.head.name
      }
      if (existsFieldName.isDefined) {
        elements(s"${key}_${existsFieldName.get}")
      } else {
        throw new NoSuchElementException
      }
    }
  }

  def isMaster = true
}

/** Memory-mapped register bank.
  *
  * A register bank is a collection of register elements, which can be accessed through a memory-mapped interface
  * [[RegBankAccessIO]]. Each register element can have multiple register fields [[RegField]]. These fields can be read
  * (and even updated) by a backdoor interface [[RegBankFieldIO]].
  *
  * @param addrWidth
  *   Bit width of write/read address.
  * @param dataWidth
  *   Bit width of write/read data.
  * @param maskUnit
  *   Bit width of write mask. Default is 0, which means no mask.
  * @param regs
  *   The configuration of register elements in the register bank.
  *
  * @example
  *   {{{
  * val uRegBank = Module(new RegBank(32, 32, regs = Seq(
  *   RegElementConfig("STATUS", addr = 0, fields = Seq(
  *     RegFieldConfig("VERSION", baseOffset = 0, bitCount = 4, accessType = ReadOnly, initValue = 0x1.U(4.W))),
  *     RegFieldConfig("ERROR", baseOffset = 4, bitCount = 1, backdoorUpdate = true)),
  *   )
  *  ))
  * uRegBank.io.access <> ... // memory-mapped access
  *
  * // Read the value of field STATUS.VERSION
  * val version = uRegBank.io.fields("STATUS.VERSION").value
  *
  * // Update the value of field STATUS.ERROR from backdoor
  * uRegBank.io.fields("STATUS.ERROR").backdoorUpdate.get.valid := true.B
  * uRegBank.io.fields("STATUS.ERROR").backdoorUpdate.get.bits  := 1.U
  *   }}}
  */
class RegBank(addrWidth: Int, dataWidth: Int, maskUnit: Int = 0, regs: Seq[RegElementConfig]) extends Module {
  if (regs.length >= 2) {
    require(
      regs.combinations(2).forall(x => x.head.addr != x.last.addr),
      "address overlap is not allowed between two register elements"
    )
  }

  val regsConfig = regs

  val io = IO(new Bundle {
    val access = Slave(new RegBankAccessIO(addrWidth, dataWidth, maskUnit))
    val fields = new RegBankFieldIO(regs)
  })

  io.access.wr.resp.valid := RegNext(io.access.wr.req.fire, false.B)
  io.access.rd.resp.valid := RegNext(io.access.rd.req.fire, false.B)

  val elemWrAddrHits = regs.map(io.access.wr.req.bits.addr === _.addr.U)
  val elemRdAddrHits = regs.map(io.access.rd.req.bits.addr === _.addr.U)

  val wrAddrNoMatch = !elemWrAddrHits.reduce(_ || _)
  val rdAddrNoMatch = !elemRdAddrHits.reduce(_ || _)

  val wrRespStatus = RegInit(false.B)
  when(io.access.wr.req.fire) {
    wrRespStatus := wrAddrNoMatch
  }.otherwise {
    wrRespStatus := false.B
  }

  val rdRespStatus = RegInit(false.B)
  when(io.access.rd.req.fire) {
    rdRespStatus := rdAddrNoMatch
  }.otherwise {
    rdRespStatus := false.B
  }

  val elemRdDatas = Wire(Vec(regs.length, UInt(dataWidth.W)))
  val rdDataNext  = MuxLookup(io.access.rd.req.bits.addr, 0.U)(regs.map(_.addr.U) zip elemRdDatas)

  val rdRespData = RegNext(rdDataNext, 0.U)
  io.access.wr.resp.bits.status := wrRespStatus
  io.access.rd.resp.bits.status := rdRespStatus
  io.access.rd.resp.bits.data   := rdRespData

  for (idxElem <- regs.indices) {
    val elemConfig   = regs(idxElem)
    val elemWrEnable = io.access.wr.req.fire && elemWrAddrHits(idxElem)
    val elemRdEnable = io.access.rd.req.fire && elemRdAddrHits(idxElem)
    val elemWrBitMask = if (io.access.hasMask) {
      val mask: UInt = io.access.wr.req.bits.mask.get
      Cat(mask.asBools.reverse.flatMap(b => Seq.fill(io.access.maskUnit)(b)))
    } else {
      Fill(dataWidth, 1.B)
    }

    val elemFields = elemConfig.fields
      .sortBy(_.baseOffset)
      .map(fieldConfig => {
        val uField = Module(new RegField(fieldConfig))
        uField.io.frontdoor.wrEnable  := elemWrEnable
        uField.io.frontdoor.rdEnable  := elemRdEnable
        uField.io.frontdoor.wrData    := io.access.wr.req.bits.data(fieldConfig.endOffset, fieldConfig.baseOffset)
        uField.io.frontdoor.wrBitMask := elemWrBitMask(fieldConfig.endOffset, fieldConfig.baseOffset)

        io.fields(s"${elemConfig.name}_${fieldConfig.name}") <> uField.io.backdoor

        uField
      })

    elemRdDatas(idxElem) := {
      var currOffset = 0
      val dataPieces = for (idx <- elemFields.indices) yield {
        val fieldConfig = elemFields(idx).config
        val fieldData   = elemFields(idx).io.frontdoor.rdData.asUInt
        val zeros       = 0.U((fieldConfig.baseOffset - currOffset).W)
        currOffset = fieldConfig.endOffset + 1
        fieldData ## zeros
      }
      Cat(dataPieces.reverse)
    }
  }
}
