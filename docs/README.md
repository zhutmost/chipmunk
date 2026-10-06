# 🐿️ CHIPMUNK Docs

These pages describe the current Scala 3 source tree. Build versions, consumer configuration, and commands are in the [project README](../README.md#build-and-installation).

## Chisel extensions

- [Bits/Data helpers, priority selection, and basic records](bits-misc.md)
- [Master/Slave interface directions](master-slave.md)
- [Flat RTL interfaces with VerilogIO](verilog-io.md)
- [Falling-edge registers](regneg.md)
- [Enum-based state machines](state-machine.md)
- [Asynchronous-assert/synchronous-deassert reset synchronization](reset-sync.md)

## Interfaces and hardware components

- [Stream/Flow handshake, pipelines, routing, and arbitration](stream.md)
- [Acorn protocol, crossbar, SRAM, and width adaptation](acorn.md)
- [AXI4/AXI4-Lite interfaces and Acorn bridges](amba.md)
- [Register bank](regbank.md)
- [SPI debugger](spi-debugger.md)

## Verification

- [ChiselSim helpers and multiple simulated clocks](tester.md)
