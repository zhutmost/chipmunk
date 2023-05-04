`timescale 1ns/1ps

module Sram2rwWrapper_$NAME #(
    localparam integer DEPTH      = $DEPTH,
    localparam integer DATA_WIDTH = $DATA_WIDTH,
    localparam integer ADDR_WIDTH = $ADDR_WIDTH,
    localparam integer MASK_WIDTH = $MASK_WIDTH,
    localparam integer MASK_UNIT  = $MASK_UNIT
)(
    input  logic                    rw0_clock,
    input  logic                    rw0_enable,
    input  logic                    rw0_write,
    input  logic [ADDR_WIDTH-1:0]   rw0_addr,
    input  logic [MASK_WIDTH-1:0]   rw0_mask,
    input  logic [DATA_WIDTH-1:0]   rw0_dataIn,
    output logic [DATA_WIDTH-1:0]   rw0_dataOut,

    input  logic                    rw1_clock,
    input  logic                    rw1_enable,
    input  logic                    rw1_write,
    input  logic [ADDR_WIDTH-1:0]   rw1_addr,
    input  logic [MASK_WIDTH-1:0]   rw1_mask,
    input  logic [DATA_WIDTH-1:0]   rw1_dataIn,
    output logic [DATA_WIDTH-1:0]   rw1_dataOut
);

`ifdef ASIC_SRAM
    logic mem_rw0_clock = rw0_clock;
    logic mem_rw1_clock = rw1_clock;

    logic                   mem_rw0_enable;
    logic                   mem_rw0_write;
    logic [ADDR_WIDTH-1:0]  mem_rw0_addr;
    logic [MASK_WIDTH-1:0]  mem_rw0_mask;
    logic [DATA_WIDTH-1:0]  mem_rw0_dataIn;
    logic                   mem_rw1_enable;
    logic                   mem_rw1_write;
    logic [ADDR_WIDTH-1:0]  mem_rw1_addr;
    logic [MASK_WIDTH-1:0]  mem_rw1_mask;
    logic [DATA_WIDTH-1:0]  mem_rw1_dataIn;

`ifdef ASIC_SRAM_SIM_INPUT_DELAY
    assign #1ps mem_rw0_enable = rw0_enable;
    assign #1ps mem_rw0_write  = rw0_write;
    assign #1ps mem_rw0_addr   = rw0_addr;
    assign #1ps mem_rw0_mask   = rw0_mask;
    assign #1ps mem_rw0_dataIn = rw0_dataIn;
    assign #1ps mem_rw1_enable = rw1_enable;
    assign #1ps mem_rw1_write  = rw1_write;
    assign #1ps mem_rw1_addr   = rw1_addr;
    assign #1ps mem_rw1_mask   = rw1_mask;
    assign #1ps mem_rw1_dataIn = rw1_dataIn;
`else // ASIC_SRAM_SIM_INPUT_DELAY
    assign mem_rw0_enable = rw0_enable;
    assign mem_rw0_write  = rw0_write;
    assign mem_rw0_addr   = rw0_addr;
    assign mem_rw0_mask   = rw0_mask;
    assign mem_rw0_dataIn = rw0_dataIn;
    assign mem_rw1_enable = rw1_enable;
    assign mem_rw1_write  = rw1_write;
    assign mem_rw1_addr   = rw1_addr;
    assign mem_rw1_mask   = rw1_mask;
    assign mem_rw1_dataIn = rw1_dataIn;
`endif // ASIC_SRAM_SIM_INPUT_DELAY
    $SRAM_MACRO_INSTANCE
`else
    SramFpga #(
        .DEPTH          (DEPTH),
        .ADDR_WIDTH     (ADDR_WIDTH),
        .DATA_WIDTH     (DATA_WIDTH),
        .MASK_UNIT      (MASK_UNIT)
    ) uMem (
        .rw0_clock      (rw0_clock),
        .rw0_enable     (rw0_enable),
        .rw0_write      (rw0_write),
        .rw0_addr       (rw0_addr),
        .rw0_mask       (rw0_mask),
        .rw0_dataIn     (rw0_dataIn),
        .rw0_dataOut    (rw0_dataOut),

        .rw1_clock      (rw1_clock),
        .rw1_enable     (rw1_enable),
        .rw1_write      (rw1_write),
        .rw1_addr       (rw1_addr),
        .rw1_mask       (rw1_mask),
        .rw1_dataIn     (rw1_dataIn),
        .rw1_dataOut    (rw1_dataOut)
    );
`endif

endmodule: Sram1rwWrapper_$NAME
