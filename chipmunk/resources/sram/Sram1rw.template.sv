`timescale 1ns/1ps

module Sram1rwWrapper_$NAME #(
    localparam integer DEPTH      = $DEPTH,
    localparam integer DATA_WIDTH = $DATA_WIDTH,
    localparam integer ADDR_WIDTH = $ADDR_WIDTH
)(
    input  logic                    rw_clock,
    input  logic                    rw_enable,
    input  logic                    rw_write,
    input  logic [ADDR_WIDTH-1:0]   rw_addr,
    input  logic [DATA_WIDTH-1:0]   rw_dataIn,
    output logic [DATA_WIDTH-1:0]   rw_dataOut
);

`ifdef ASIC_SRAM
    logic mem_rw_clock = rw_clock;

    logic                   mem_rw_enable;
    logic                   mem_rw_write;
    logic [ADDR_WIDTH-1:0]  mem_rw_addr;
    logic [DATA_WIDTH-1:0]  mem_rw_dataIn;

`ifdef ASIC_SRAM_SIM_INPUT_DELAY
    assign #1ps mem_rw_enable = rw_enable;
    assign #1ps mem_rw_write  = rw_write;
    assign #1ps mem_rw_addr   = rw_addr;
    assign #1ps mem_rw_dataIn = rw_dataIn;
`else // ASIC_SRAM_SIM_INPUT_DELAY
    assign mem_rw_enable = rw_enable;
    assign mem_rw_write  = rw_write;
    assign mem_rw_addr   = rw_addr;
    assign mem_rw_dataIn = rw_dataIn;
`endif // ASIC_SRAM_SIM_INPUT_DELAY
    $SRAM_MACRO_INSTANCE
`else
    SramFpga #(
        .DEPTH          (DEPTH),
        .ADDR_WIDTH     (ADDR_WIDTH),
        .DATA_WIDTH     (DATA_WIDTH),
        .MASK_UNIT      (DATA_WIDTH)
    ) uMem (
        .rw0_clock      (rw_clock),
        .rw0_enable     (rw_enable),
        .rw0_write      (rw_write),
        .rw0_addr       (rw_addr),
        .rw0_mask       (1'b1),
        .rw0_dataIn     (rw_dataIn),
        .rw0_dataOut    (rw_dataOut),

        .rw1_clock      (1'b0),
        .rw1_enable     (1'b0),
        .rw1_write      (1'b0),
        .rw1_addr       ('0),
        .rw1_mask       ('0),
        .rw1_dataIn     ('0),
        .rw1_dataOut    ()
    );
`endif

endmodule: Sram1rwWrapper_$NAME
