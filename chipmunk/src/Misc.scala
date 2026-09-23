package chipmunk

import chisel3.*
import chisel3.util.{PriorityMux}

/** A Bundle with no payload fields. Useful as a typed empty payload. */
final class EmptyBundle extends Bundle

/** Alias for [[chisel3.util.PriorityMux]].
  *
  * @note
  *   Chisel has many muxes named `MuxXxx`, but this is the only one that is named as `XxxMux`.
  */
val MuxPriority: PriorityMux.type = PriorityMux
