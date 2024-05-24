package chipmunk.test
package stream

import chipmunk._
import chipmunk.stream._
import chipmunk.tester._
import chisel3._
import chisel3.util.DecoupledIO

class StreamIOSpec extends ChipmunkFlatSpec with VerilatorTestRunner {
  "Stream" should "be created from DecoupledIO or Chisel types" in {
    compile(new Module {
      val io = IO(new Bundle {
        val in  = Flipped(DecoupledIO(UInt(8.W)))
        val out = Master(Stream(UInt(8.W))) // created from Chisel types
      })
      val inStream  = io.in.toStream // created from DecoupledIO
      val outStream = Wire(Stream(chiselTypeOf(io.in.bits)))
      outStream connectFrom inStream
      io.out <> outStream
    })
  }
}
