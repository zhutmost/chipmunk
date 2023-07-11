`timescale 1ns/1ps

module Sram1r1wWrapper_$NAME#(
    localparam integer DEPTH=$DEPTH,
    localparam integer DATA_WIDTH=$DATA_WIDTH,
    localparam integer ADDR_WIDTH=$ADDR_WIDTH,
    localparam integer MASK_WIDTH=$MASK_WIDTH,
    localparam integer MASK_UNIT=$MASK_UNIT
)(
    input logic wr_clock,
    input logic wr_enable,
    input logic[ADDR_WIDTH-1:0] wr_addr,
    input logic[MASK_WIDTH-1:0] wr_mask,
    input logic[DATA_WIDTH-1:0] wr_dataIn,

    input logic rd_clock,
    input logic rd_enable,
    input logic[ADDR_WIDTH-1:0] rd_addr,
    output logic[DATA_WIDTH-1:0] rd_dataOut
);

`ifdef ASIC_SRAM
    logic mem_wr_clock = wr_clock;
    logic mem_rd_clock = rd_clock;

    logic                   mem_wr_enable;
    logic [ADDR_WIDTH-1:0]  mem_wr_addr;
    logic [MASK_WIDTH-1:0]  mem_wr_mask;
    logic [DATA_WIDTH-1:0]  mem_wr_dataIn;
    logic                   mem_rd_enable;
    logic [ADDR_WIDTH-1:0]  mem_rd_addr;

`ifdef ASIC_SRAM_SIM_INPUT_DELAY
    assign #1ps mem_wr_enable = wr_enable;
    assign #1ps mem_wr_addr   = wr_addr;
    assign #1ps mem_wr_dataIn = wr_dataIn;
    assign #1ps mem_rd_enable = rd_enable;
    assign #1ps mem_rd_addr   = rd_addr;
    assign #1ps mem_wr_mask   = wr_mask;
`else // ASIC_SRAM_SIM_INPUT_DELAY
    assign mem_wr_enable = wr_enable;
    assign mem_wr_addr   = wr_addr;
    assign mem_wr_dataIn = wr_dataIn;
    assign mem_wr_mask   = wr_mask;
    assign mem_rd_enable = rd_enable;
    assign mem_rd_addr   = rd_addr;
`endif // ASIC_SRAM_SIM_INPUT_DELAY
    $SRAM_MACRO_INSTANCE
`else
    SramFpga#(
        .DEPTH(DEPTH),
        .ADDR_WIDTH(ADDR_WIDTH),
        .DATA_WIDTH(DATA_WIDTH),
        .MASK_UNIT(MASK_UNIT)
    ) uMem(
        .rw0_clock(wr_clock),
        .rw0_enable(wr_enable),
        .rw0_write(1'b1),
        .rw0_addr(wr_addr),
        .rw0_mask(wr_mask),
        .rw0_dataIn(wr_dataIn),
        .rw0_dataOut(),

        .rw1_clock(rd_clock),
        .rw1_enable(rd_enable),
        .rw1_write(1'b0),
        .rw1_addr(rd_addr),
        .rw1_mask('0),
        .rw1_dataIn('0),
        .rw1_dataOut(rd_dataOut)
    );
`endif

endmodule: Sram1r1wWrapper_$NAME
