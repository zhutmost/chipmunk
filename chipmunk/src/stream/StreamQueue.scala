package chipmunk
package stream

import chisel3._
import chisel3.util._

import scala.annotation.nowarn

object StreamQueue {

  /** Create a [[Queue]] and supply a [[StreamIO]] containing the product.
    *
    * It is a wrapper of [[Queue]] with [[StreamIO]] instead of [[DecoupledIO]].
    *
    * @param enq
    *   input (enqueue) interface to the queue, also determines type of queue elements.
    * @param entries
    *   depth (number of elements) of the queue
    * @param pipe
    *   True if a single entry queue can run at full throughput (like a pipeline). The `ready` signals are
    *   combinationally coupled.
    * @param flow
    *   True if the inputs can be consumed on the same cycle (the inputs "flow" through the queue immediately). The
    *   `valid` signals are coupled.
    * @param useSyncReadMem
    *   True uses SyncReadMem instead of Mem as an internal memory element.
    * @param flush
    *   Optional [[Bool]] signal, if defined, the [[Queue.hasFlush]] will be true, and connect correspond signal to
    *   [[Queue]] instance.
    * @return
    *   output (dequeue) interface from the queue.
    *
    * @example
    *   {{{consumer.io.in <> Queue(producer.io.out, 16)}}}
    */
  def apply[T <: Data](
    enq: ReadyValidIO[T],
    entries: Int = 2,
    pipe: Boolean = false,
    flow: Boolean = false,
    useSyncReadMem: Boolean = false,
    flush: Option[Bool] = None
  ): StreamIO[T] = {
    val ret: StreamIO[T] = Wire(Stream(chiselTypeOf(enq.bits)))
    val deq = if (entries == 0) {
      enq
    } else {
      val q = Module(new Queue(chiselTypeOf(enq.bits), entries, pipe, flow, useSyncReadMem, flush.isDefined))
      q.io.flush.zip(flush).foreach(f => f._1 := f._2)
      q.io.enq.valid := enq.valid // not using <> so that override is allowed
      q.io.enq.bits  := enq.bits
      enq.ready      := q.io.enq.ready
      q.io.deq
    }
    ret.bits  := deq.bits
    ret.valid := deq.valid
    deq.ready := ret.ready
    ret
  }
}
