/** Block RAM for Xilinx FPGA. */
module SramFpga #(
    parameter int unsigned DEPTH      = 32'd1024,
    parameter int unsigned DATA_WIDTH = 32'd128,
    parameter int unsigned MASK_UNIT  = 32'd8,
    parameter int unsigned ADDR_WIDTH = 32'd10,
    parameter int unsigned MASK_WIDTH = (DATA_WIDTH + MASK_UNIT - 32'd1) / MASK_UNIT
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
    logic write_enable_0 = rw0_enable && rw0_write;
    logic read_enable_0 = rw0_enable && !rw0_write;
    logic write_enable_1 = rw1_enable && rw1_write;
    logic read_enable_1 = rw1_enable && !rw1_write;

    logic [DATA_WIDTH-1:0] dataOutReg_0, dataOutReg_1;
    logic [DATA_WIDTH-1:0] array [DEPTH-1:0];

    always_ff @(posedge rw0_clock) begin
        if (read_enable_0) begin
            dataOutReg_0 <= array[rw0_addr];
        end
    end

    generate
        for (genvar i = 0; i < MASK_WIDTH; i += 1) begin: gen_subarray_0
            logic sub_write_enable = write_enable_0 && rw0_mask[i];
            always_ff @(posedge rw0_clock) begin
                if (sub_write_enable) begin
                    array[rw0_addr][i*MASK_UNIT+:MASK_UNIT] <= rw0_dataIn[i*MASK_UNIT+:MASK_UNIT];
                end
            end
        end
    endgenerate

    always_ff @(posedge rw1_clock) begin
        if (read_enable_1) begin
            dataOutReg_1 <= array[rw1_addr];
        end
    end

    generate
        for (genvar i = 0; i < MASK_WIDTH; i += 1) begin: gen_subarray_1
            logic sub_write_enable = write_enable_1 && rw1_mask[i];
            always_ff @(posedge rw1_clock) begin
                if (sub_write_enable) begin
                    array[rw1_addr][i*MASK_UNIT+:MASK_UNIT] <= rw1_dataIn[i*MASK_UNIT+:MASK_UNIT];
                end
            end
        end
    endgenerate

    assign rw0_dataOut = dataOutReg_0;
    assign rw1_dataOut = dataOutReg_1;

endmodule
