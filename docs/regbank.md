# Register Bank

`chipmunk.regbank.RegBank` is a memory-mapped register endpoint for `chipmunk.acorn`.
Each register contains one or more fields with independent access rules and optional hardware updates.

## Configuration

Configurations are ordinary Scala values and can be created outside a Chisel Module.
Addresses are local byte offsets: a 32-bit bank uses offsets `0x00`, `0x04`, `0x08`, and so on.
Crossbar translates global addresses into these local offsets.

```scala
import chisel3.*
import chipmunk.acorn.AcornParams
import chipmunk.regbank.*
import chipmunk.regbank.RegFieldAccessType.*

val config = RegBankConfig(AcornParams(32, 12), Seq(
  RegElementConfig("CONTROL", addr = 0x00, bitCount = 32),
  RegElementConfig("STATUS", addr = 0x04, fields = Seq(
    RegFieldConfig("VERSION", 0, 8, initValue = 1, accessType = ReadOnly),
    RegFieldConfig("ERROR", 8, 1, accessType = WriteOneClear, backdoorUpdate = true)
  ))
))

// Inside a Module:
val bank = Module(new RegBank(config))
// bank.io.access <> master
val control = bank.io.fields("CONTROL").value
val error = bank.io.fields("STATUS_ERROR")
error.backdoorUpdate.get.valid := hardwareUpdate
error.backdoorUpdate.get.bits := hardwareValue
```

The existing `new RegBank(addrWidth, dataWidth, regs)` constructor is also available.
Reset values are `BigInt`, so use `initValue = 0x1234` rather than `0x1234.U`.
`RegFieldCollisionMode` is a Scala enum; its six existing priority names are retained.

The map must be nonempty. Register names and addresses are unique; field names are unique within each register.
Names start with a letter or underscore and contain only letters, digits, and underscores.
Addresses must fit the address width and align to a data word. Field ranges cannot overlap or exceed the data width,
and reset values must fit their fields. Flattened `REG_FIELD` names and single-field aliases must not conflict.
Unused bits read as zero and ignore writes.

## Software access

- Every accepted read or write command has one response. Unmapped or unaligned addresses return an error; read errors return zero.
- Reads sample the current value when the command transfers. Read-clear and read-set side effects occur at that same transfer,
  and the response contains the value before the side effect. A simultaneous read and write also reads the previous value.
- Writes use Acorn byte strobes. A zero field mask leaves that field unchanged and does not participate in update arbitration.
  An all-zero strobe still receives a normal write response.
- Read-only fields ignore writes; write-only fields read as zero. These accesses do not produce bus errors.
- WriteOnce and WriteOnlyOnce lock the entire field on its first accepted software write with a nonzero field mask,
  including a partial write. Zero-mask writes do not lock it. Reset unlocks it; hardware updates remain allowed.
- Read and write responses have separate one-entry buffers. They remain stable under backpressure; side effects are not repeated.
  With responses accepted continuously, the bank can accept one read and one write per cycle. Read and write have no implicit ordering.

## Hardware access

Use `fields("REG_FIELD")`, or `fields("REG")` for a single-field register.
`value` exposes the current stored value. `isBeingRead` pulses on an accepted read of a readable field;
`isBeingWritten` pulses on an accepted write with a nonzero field mask, even if the access rule ignores it.

With `backdoorUpdate = true`, the optional Flow port assigns the whole field on each valid cycle.
It is a hardware access port with no backpressure. Concurrent updates use `collisionMode`, which orders software write,
read side effect, and hardware assignment; default priority is hardware, then write, then read.
A zero-mask or locked software write does not block other updates. All signals share the bank's clock and reset.

## Tests

```bash
mill chipmunk.test.testOnly \
  chipmunk.test.regbank.RegBankConfigSpec \
  chipmunk.test.regbank.RegBankSpec
```

The tests cover configuration errors, all 25 built-in access rules, byte masks, write-once reset, update collisions,
response backpressure, read snapshots, and two-master access through AcornCrossbar.
