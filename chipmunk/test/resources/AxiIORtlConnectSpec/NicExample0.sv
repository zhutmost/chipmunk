`default_nettype none

module NicExample0 (
    input  var logic        ACLOCK,
    input  var logic        ARESETN,

    // Slave: s_axi_spi

    input  var logic [1:0]  AWID_s_axi_spi,
    input  var logic [31:0] AWADDR_s_axi_spi,
    input  var logic [7:0]  AWLEN_s_axi_spi,
    input  var logic [2:0]  AWSIZE_s_axi_spi,
    input  var logic [1:0]  AWBURST_s_axi_spi,
    input  var logic        AWLOCK_s_axi_spi,
    input  var logic [3:0]  AWCACHE_s_axi_spi,
    input  var logic [2:0]  AWPROT_s_axi_spi,
    input  var logic        AWVALID_s_axi_spi,
    output var logic        AWREADY_s_axi_spi,

    input  var logic [127:0] WDATA_s_axi_spi,
    input  var logic [15:0] WSTRB_s_axi_spi,
    input  var logic        WLAST_s_axi_spi,
    input  var logic        WVALID_s_axi_spi,
    output var logic        WREADY_s_axi_spi,

    output var logic [1:0]  BID_s_axi_spi,
    output var logic [1:0]  BRESP_s_axi_spi,
    output var logic        BVALID_s_axi_spi,
    input  var logic        BREADY_s_axi_spi,

    input  var logic [1:0]  ARID_s_axi_spi,
    input  var logic [31:0] ARADDR_s_axi_spi,
    input  var logic [7:0]  ARLEN_s_axi_spi,
    input  var logic [2:0]  ARSIZE_s_axi_spi,
    input  var logic [1:0]  ARBURST_s_axi_spi,
    input  var logic        ARLOCK_s_axi_spi,
    input  var logic [3:0]  ARCACHE_s_axi_spi,
    input  var logic [2:0]  ARPROT_s_axi_spi,
    input  var logic        ARVALID_s_axi_spi,
    output var logic        ARREADY_s_axi_spi,

    output var logic [1:0]  RID_s_axi_spi,
    output var logic [127:0] RDATA_s_axi_spi,
    output var logic [1:0]  RRESP_s_axi_spi,
    output var logic        RLAST_s_axi_spi,
    output var logic        RVALID_s_axi_spi,
    input  var logic        RREADY_s_axi_spi,

    // Master: m_axi_sram

    output var logic [1:0]  AWID_m_axi_sram,
    output var logic [31:0] AWADDR_m_axi_sram,
    output var logic [7:0]  AWLEN_m_axi_sram,
    output var logic [2:0]  AWSIZE_m_axi_sram,
    output var logic [1:0]  AWBURST_m_axi_sram,
    output var logic        AWLOCK_m_axi_sram,
    output var logic [3:0]  AWCACHE_m_axi_sram,
    output var logic [2:0]  AWPROT_m_axi_sram,
    output var logic        AWVALID_m_axi_sram,
    input  var logic        AWREADY_m_axi_sram,

    output var logic [127:0] WDATA_m_axi_sram,
    output var logic [15:0] WSTRB_m_axi_sram,
    output var logic        WLAST_m_axi_sram,
    output var logic        WVALID_m_axi_sram,
    input  var logic        WREADY_m_axi_sram,

    input  var logic [1:0]  BID_m_axi_sram,
    input  var logic [1:0]  BRESP_m_axi_sram,
    input  var logic        BVALID_m_axi_sram,
    output var logic        BREADY_m_axi_sram,

    output var logic [1:0]  ARID_m_axi_sram,
    output var logic [31:0] ARADDR_m_axi_sram,
    output var logic [7:0]  ARLEN_m_axi_sram,
    output var logic [2:0]  ARSIZE_m_axi_sram,
    output var logic [1:0]  ARBURST_m_axi_sram,
    output var logic        ARLOCK_m_axi_sram,
    output var logic [3:0]  ARCACHE_m_axi_sram,
    output var logic [2:0]  ARPROT_m_axi_sram,
    output var logic        ARVALID_m_axi_sram,
    input  var logic        ARREADY_m_axi_sram,

    input  var logic [1:0]  RID_m_axi_sram,
    input  var logic [127:0] RDATA_m_axi_sram,
    input  var logic [1:0]  RRESP_m_axi_sram,
    input  var logic        RLAST_m_axi_sram,
    input  var logic        RVALID_m_axi_sram,
    output var logic        RREADY_m_axi_sram
);

    assign AWID_m_axi_sram    = AWID_s_axi_spi;
    assign AWADDR_m_axi_sram  = AWADDR_s_axi_spi;
    assign AWLEN_m_axi_sram   = AWLEN_s_axi_spi;
    assign AWSIZE_m_axi_sram  = AWSIZE_s_axi_spi;
    assign AWBURST_m_axi_sram = AWBURST_s_axi_spi;
    assign AWLOCK_m_axi_sram  = AWLOCK_s_axi_spi;
    assign AWCACHE_m_axi_sram = AWCACHE_s_axi_spi;
    assign AWPROT_m_axi_sram  = AWPROT_s_axi_spi;
    assign AWVALID_m_axi_sram = AWVALID_s_axi_spi;
    assign AWREADY_s_axi_spi  = AWREADY_m_axi_sram;

    assign WDATA_m_axi_sram  = WDATA_s_axi_spi;
    assign WSTRB_m_axi_sram  = WSTRB_s_axi_spi;
    assign WLAST_m_axi_sram  = WLAST_s_axi_spi;
    assign WVALID_m_axi_sram = WVALID_s_axi_spi;
    assign WREADY_s_axi_spi  = WREADY_m_axi_sram;

    assign BID_s_axi_spi     = BID_m_axi_sram;
    assign BRESP_s_axi_spi   = BRESP_m_axi_sram;
    assign BVALID_s_axi_spi  = BVALID_m_axi_sram;
    assign BREADY_m_axi_sram = BREADY_s_axi_spi;

    assign ARID_m_axi_sram    = ARID_s_axi_spi;
    assign ARADDR_m_axi_sram  = ARADDR_s_axi_spi;
    assign ARLEN_m_axi_sram   = ARLEN_s_axi_spi;
    assign ARSIZE_m_axi_sram  = ARSIZE_s_axi_spi;
    assign ARBURST_m_axi_sram = ARBURST_s_axi_spi;
    assign ARLOCK_m_axi_sram  = ARLOCK_s_axi_spi;
    assign ARCACHE_m_axi_sram = ARCACHE_s_axi_spi;
    assign ARPROT_m_axi_sram  = ARPROT_s_axi_spi;
    assign ARVALID_m_axi_sram = ARVALID_s_axi_spi;
    assign ARREADY_s_axi_spi  = ARREADY_m_axi_sram;

    assign RID_s_axi_spi     = RID_m_axi_sram;
    assign RDATA_s_axi_spi   = RDATA_m_axi_sram;
    assign RRESP_s_axi_spi   = RRESP_m_axi_sram;
    assign RLAST_s_axi_spi   = RLAST_m_axi_sram;
    assign RVALID_s_axi_spi  = RVALID_m_axi_sram;
    assign RREADY_m_axi_sram = RREADY_s_axi_spi;

endmodule
