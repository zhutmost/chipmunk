`default_nettype none

module NicExample3 #(
    parameter int S00_AW = 32,
    parameter int S00_DW = 32,
    parameter int M00_AW = 32,
    parameter int M00_DW = 32
)(
    input  var logic                clock,
    input  var logic                resetn,

    input  var logic [S00_AW-1 : 0] s00_paddr,
    input  var logic [2 : 0]        s00_pprot,
    input  var logic [3 : 0]        s00_pselx,
    input  var logic                s00_penable,
    input  var logic                s00_pwrite,
    input  var logic [S00_DW-1 : 0] s00_pwdata,
    input  var logic [S00_DW/8-1 : 0] s00_pstrb,
    output var logic                s00_pready,
    output var logic                s00_pslverr,
    output var logic [S00_DW-1 : 0] s00_prdata,

    output var logic [M00_AW-1 : 0] m00_paddr,
    output var logic [3 : 0]        m00_psel,
    output var logic                m00_penable,
    output var logic                m00_pwrite,
    output var logic [M00_DW-1 : 0] m00_pwdata,
    input  var logic                m00_pready,
    input  var logic                m00_pslverr,
    input  var logic [M00_DW-1 : 0] m00_prdata
);

    always_comb begin
        m00_paddr = s00_paddr;
        m00_psel = s00_pselx;
        m00_penable = s00_penable;
        m00_pwrite = s00_pwrite;
        m00_pwdata = s00_pwdata;
        s00_pready = m00_pready;
        s00_pslverr = m00_pslverr;
        s00_prdata = m00_prdata;
    end

endmodule : NicExample3
