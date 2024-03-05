`default_nettype none

module NicExample2 #(
    parameter int S00_AW = 32,
    parameter int S00_DW = 32,
    parameter int M00_AW = 32,
    parameter int M00_DW = 32
)(
    input  var logic                clock,
    input  var logic                resetn,

    input  var logic [S00_AW-1 : 0] s00_axi_awaddr,
    input  var logic        [2 : 0] s00_axi_awprot,
    input  var logic                s00_axi_awvalid,
    output var logic                s00_axi_awready,
    input  var logic [S00_DW-1 : 0] s00_axi_wdata,
    input  var logic [(S00_DW/8)-1 : 0] s00_axi_wstrb,
    input  var logic                s00_axi_wvalid,
    output var logic                s00_axi_wready,
    output var logic [1 : 0]        s00_axi_bresp,
    output var logic                s00_axi_bvalid,
    input  var logic                s00_axi_bready,
    input  var logic [S00_AW-1 : 0] s00_axi_araddr,
    input  var logic [2 : 0]        s00_axi_arprot,
    input  var logic                s00_axi_arvalid,
    output var logic                s00_axi_arready,
    output var logic [S00_DW-1 : 0] s00_axi_rdata,
    output var logic [1 : 0]        s00_axi_rresp,
    output var logic                s00_axi_rvalid,
    input  var logic                s00_axi_rready,

    output var logic [S00_AW-1 : 0] m00_axi_awaddr,
    output var logic        [2 : 0] m00_axi_awprot,
    output var logic                m00_axi_awvalid,
    input  var logic                m00_axi_awready,
    output var logic [S00_DW-1 : 0] m00_axi_wdata,
    output var logic [(S00_DW/8)-1 : 0] m00_axi_wstrb,
    output var logic                m00_axi_wvalid,
    input  var logic                m00_axi_wready,
    input  var logic [1 : 0]        m00_axi_bresp,
    input  var logic                m00_axi_bvalid,
    output var logic                m00_axi_bready,
    output var logic [S00_AW-1 : 0] m00_axi_araddr,
    output var logic [2 : 0]        m00_axi_arprot,
    output var logic                m00_axi_arvalid,
    input  var logic                m00_axi_arready,
    input  var logic [S00_DW-1 : 0] m00_axi_rdata,
    input  var logic [1 : 0]        m00_axi_rresp,
    input  var logic                m00_axi_rvalid,
    output var logic                m00_axi_rready
);

    always_comb begin
        m00_axi_awaddr  = s00_axi_awaddr;
        m00_axi_awprot  = s00_axi_awprot;
        m00_axi_awvalid = s00_axi_awvalid;
        s00_axi_awready = m00_axi_awready;
        m00_axi_wdata   = s00_axi_wdata;
        m00_axi_wstrb   = s00_axi_wstrb;
        m00_axi_wvalid  = s00_axi_wvalid;
        s00_axi_wready  = m00_axi_wready;
        s00_axi_bresp   = m00_axi_bresp;
        s00_axi_bvalid  = m00_axi_bvalid;
        m00_axi_bready  = s00_axi_bready;
        m00_axi_araddr  = s00_axi_araddr;
        m00_axi_arprot  = s00_axi_arprot;
        m00_axi_arvalid = s00_axi_arvalid;
        s00_axi_arready = m00_axi_arready;
        s00_axi_rdata   = m00_axi_rdata;
        s00_axi_rresp   = m00_axi_rresp;
        s00_axi_rvalid  = m00_axi_rvalid;
        m00_axi_rready  = s00_axi_rready;
    end

endmodule : NicExample2
