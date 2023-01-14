package chipmunk

package iocell.tsmc

import chipmunk.iocell.IOCellLibrary

/** TSMC TPHN28HPCPGV18 I/O Cell Library.
  *
  * The TPHN28HPCPGV18 library is designed to optimize I/O performance with core voltage of 0.9V in typical case, and
  * I/O voltage of 1.8V in typical case in the TSMC 28nm 0.9V/1.8V High Performance Compact Mobile Computing process.
  *
  * Only the analog I/O cells and the digital tri-state cells are included. In other words, the
  * power/ground/corner/clamp/filler cells are excluded.
  * @param verilogResources
  *   the Verilog model of I/O cells provided by the foundry. Left it blank if you don't want specify one file during
  *   exploration, but this may fail the tests.
  */
object TPHN28HPCPGV18 extends IOCellLibrary {
  class RenamedTriStateIOCell extends TriStateIOCell(false, true, true) {
    pad.suggestName("PAD")
    oe.get.suggestName("OEN")
    pe.get.suggestName("REN")
    out.suggestName("C")
    in.suggestName("I")
  }

  class RenamedAnalogIOCell extends AnalogIOCell {
    inout.suggestName("AIO")
  }

  /** Tri-State 4mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW04DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW08DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW12DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW16DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW04DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW08DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW12DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Enable Controlled Pull-Down, Fail-Safe */
  class PDDW16DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW04DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW08DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW12DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW16DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW04DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW08DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW12DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Enable Controlled Pull-Up, Fail-Safe */
  class PDUW16DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW04SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW08SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW12SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW16SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW04SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW08SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW12SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDDW16SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW04SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW08SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW12SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW16SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW04SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW08SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW12SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input and Enable Controlled Pull-Down, Fail-Safe
    */
  class PDUW16SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW04DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW08DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW12DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW16DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW04DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW08DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW12DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Down, Fail-Safe */
  class PRDW16DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW04DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW08DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW12DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW16DGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW04DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW08DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW12DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Limited Slew Rate, and Enable Controlled Pull-Up, Fail-Safe */
  class PRUW16DGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW04SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW08SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW12SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW16SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW04SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW08SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW12SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRDW16SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW04SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW08SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW12SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW16SDGZ_V_G extends RenamedTriStateIOCell

  /** Tri-State 4mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW04SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 8mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW08SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 12mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW12SDGZ_H_G extends RenamedTriStateIOCell

  /** Tri-State 16mA GPIO Pad with Schmitt Trigger Input, Limited Skew Rate and Enable Controlled Pull-Down, Fail-Safe
    */
  class PRUW16SDGZ_H_G extends RenamedTriStateIOCell

  /** Analog I/O Cell with Core Voltage */
  class PDB3AC_V_G extends RenamedAnalogIOCell

  /** Analog I/O Cell with Core Voltage */
  class PDB3AC_H_G extends RenamedAnalogIOCell

  /** Analog I/O Cell with I/O Voltage */
  class PDB3A_V_G extends RenamedAnalogIOCell

  /** Analog I/O Cell with I/O Voltage */
  class PDB3A_H_G extends RenamedAnalogIOCell
}
