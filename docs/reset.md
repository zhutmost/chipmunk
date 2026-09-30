# Reset release

`ResetSync` and `ResetReleaseHold` use active-high asynchronous reset requests. Convert an active-low pin at the
system boundary. Both modules have explicit clock and reset ports; neither uses the enclosing module's implicit
clock or reset.

## ResetSync

`ResetSync(stages = 2)` has these ports:

| Port | Direction | Meaning |
| --- | --- | --- |
| `clock: Clock` | Input | Destination clock |
| `asyncReset: AsyncReset` | Input | Asynchronous reset request |
| `resetOut: AsyncReset` | Output | Asserts without a clock; releases after `stages` rising edges |
| `released: Bool` | Output | Inverse of `resetOut`, for use in the destination domain |

All stages are asynchronously set to one. When `asyncReset` goes low, a zero advances through the stages on
successive rising edges. The output remains asserted while the destination clock is stopped. Use at least two stages
and share one synchronized reset among consumers in the same clock/reset domain.

```scala
val coreResetSync = Module(new ResetSync(stages = 2))
coreResetSync.io.clock      := coreClock
coreResetSync.io.asyncReset := powerOnReset

withClockAndReset(coreClock, coreResetSync.io.resetOut) {
  // Core logic
}
```

`released` only means that the local reset has been removed. An IP's initialization may finish later. If another
clock domain needs this status, transfer it using an appropriate CDC mechanism.

## ResetReleaseHold

`ResetReleaseHold(stableCycles)` qualifies a release condition in a reliable control clock domain, often an
always-on domain. It is not a synchronizer for the destination clock.

| Port | Direction | Meaning |
| --- | --- | --- |
| `controlClock: Clock` | Input | Clock used to count stable conditions |
| `asyncReset: AsyncReset` | Input | Immediate reset request and reset for the qualifier |
| `releaseOk: Bool` | Input | Release permission, already valid in `controlClock`'s domain |
| `resetRequest: AsyncReset` | Output | Held high until `releaseOk` is high for `stableCycles` rising edges |
| `qualified: Bool` | Output | The stable condition has been met in the control domain |

If `releaseOk` becomes false, its counter is cleared at the next control clock edge and `resetRequest` asserts.
An assertion of `asyncReset` bypasses the counter and asserts `resetRequest` without any clock. A fault that must
assert reset even when `controlClock` is stopped therefore belongs on the `asyncReset` path, not only on
`releaseOk`. Synchronize conditions from other clock domains before using them as `releaseOk`.

```scala
val releaseHold = Module(new ResetReleaseHold(stableCycles = 8))
releaseHold.io.controlClock := aonClock
releaseHold.io.asyncReset   := powerOnReset
releaseHold.io.releaseOk    := powerAndClockReady // Already valid in the AON domain

val coreResetSync = Module(new ResetSync(stages = 2))
coreResetSync.io.clock      := coreClock
coreResetSync.io.asyncReset := releaseHold.io.resetRequest
```

The eight stable cycles count `aonClock` edges. After the request is removed, the reset synchronizer additionally
waits for two `coreClock` edges. For an unqualified POR reset, use `ResetSync` alone.

The reset synchronization stages require platform-specific CDC/RDC review and physical attributes or constraints.
Do not assume that Scala or Chisel types alone provide a physical metastability guarantee.
