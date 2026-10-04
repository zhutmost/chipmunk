package chipmunk
package stream

import chisel3.*
import chisel3.experimental.SourceInfo
import chisel3.util.{Queue, ReadyValidIO}

/** Stream-returning factory for Chisel Queue. Queue storage and handshake behavior are implemented by Chisel. */
object StreamQueue {

  /** Buffer transactions, optionally coupling ready (`pipe`) or bypassing an empty queue (`flow`).
    *
    * A zero-depth queue is an unbuffered adapter: its producer must already obey the StreamIO protocol. Flush cancels
    * queued transactions on the active edge, including an offered output; users must coordinate cancellation.
    */
  def apply[T <: Data](
    enq: ReadyValidIO[T],
    entries: Int = 2,
    pipe: Boolean = false,
    flow: Boolean = false,
    useSyncReadMem: Boolean = false,
    flush: Option[Bool] = None,
  )(using SourceInfo): StreamIO[T] = {
    require(entries >= 0, "Queue depth must be nonnegative.")
    require(entries > 0 || flush.isEmpty, "A zero-depth queue cannot be flushed.")
    Stream.from(Queue(enq, entries, pipe, flow, useSyncReadMem, flush))
  }
}
