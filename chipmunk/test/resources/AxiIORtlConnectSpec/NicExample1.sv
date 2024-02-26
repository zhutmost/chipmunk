`default_nettype none

module NicExample1 (
    input  var logic        clock,
    input  var logic        resetn,

    // Slave: s_axi_spi

    input  var logic [1:0]  s_axi_spi_AWID,
    input  var logic [31:0] s_axi_spi_AWADDR,
    input  var logic [7:0]  s_axi_spi_AWLEN,
    input  var logic [2:0]  s_axi_spi_AWSIZE,
    input  var logic [1:0]  s_axi_spi_AWBURST,
    input  var logic        s_axi_spi_AWLOCK,
    input  var logic [3:0]  s_axi_spi_AWCACHE,
    input  var logic [2:0]  s_axi_spi_AWPROT,
    input  var logic        s_axi_spi_AWVALID,
    output var logic        s_axi_spi_AWREADY,

    input  var logic [127:0] s_axi_spi_WDATA,
    input  var logic [15:0] s_axi_spi_WSTRB,
    input  var logic        s_axi_spi_WLAST,
    input  var logic        s_axi_spi_WVALID,
    output var logic        s_axi_spi_WREADY,

    output var logic [1:0]  s_axi_spi_BID,
    output var logic [1:0]  s_axi_spi_BRESP,
    output var logic        s_axi_spi_BVALID,
    input  var logic        s_axi_spi_BREADY,

    input  var logic [1:0]  s_axi_spi_ARID,
    input  var logic [31:0] s_axi_spi_ARADDR,
    input  var logic [7:0]  s_axi_spi_ARLEN,
    input  var logic [2:0]  s_axi_spi_ARSIZE,
    input  var logic [1:0]  s_axi_spi_ARBURST,
    input  var logic        s_axi_spi_ARLOCK,
    input  var logic [3:0]  s_axi_spi_ARCACHE,
    input  var logic [2:0]  s_axi_spi_ARPROT,
    input  var logic        s_axi_spi_ARVALID,
    output var logic        s_axi_spi_ARREADY,

    output var logic [1:0]  s_axi_spi_RID,
    output var logic [127:0] s_axi_spi_RDATA,
    output var logic [1:0]  s_axi_spi_RRESP,
    output var logic        s_axi_spi_RLAST,
    output var logic        s_axi_spi_RVALID,
    input  var logic        s_axi_spi_RREADY,

    // Master: m_axi_sram

    output var logic [1:0]  m_axi_sram_AWID,
    output var logic [31:0] m_axi_sram_AWADDR,
    output var logic [7:0]  m_axi_sram_AWLEN,
    output var logic [2:0]  m_axi_sram_AWSIZE,
    output var logic [1:0]  m_axi_sram_AWBURST,
    output var logic        m_axi_sram_AWLOCK,
    output var logic [3:0]  m_axi_sram_AWCACHE,
    output var logic [2:0]  m_axi_sram_AWPROT,
    output var logic [3:0]  m_axi_sram_AWREGION,
    output var logic        m_axi_sram_AWVALID,
    input  var logic        m_axi_sram_AWREADY,

    output var logic [127:0] m_axi_sram_WDATA,
    output var logic [15:0] m_axi_sram_WSTRB,
    output var logic        m_axi_sram_WLAST,
    output var logic        m_axi_sram_WVALID,
    input  var logic        m_axi_sram_WREADY,

    input  var logic [1:0]  m_axi_sram_BID,
    input  var logic [1:0]  m_axi_sram_BRESP,
    input  var logic        m_axi_sram_BVALID,
    output var logic        m_axi_sram_BREADY,

    output var logic [1:0]  m_axi_sram_ARID,
    output var logic [31:0] m_axi_sram_ARADDR,
    output var logic [7:0]  m_axi_sram_ARLEN,
    output var logic [2:0]  m_axi_sram_ARSIZE,
    output var logic [1:0]  m_axi_sram_ARBURST,
    output var logic        m_axi_sram_ARLOCK,
    output var logic [3:0]  m_axi_sram_ARCACHE,
    output var logic [2:0]  m_axi_sram_ARPROT,
    output var logic [3:0]  m_axi_sram_ARREGION,
    output var logic        m_axi_sram_ARVALID,
    input  var logic        m_axi_sram_ARREADY,

    input  var logic [1:0]  m_axi_sram_RID,
    input  var logic [127:0] m_axi_sram_RDATA,
    input  var logic [1:0]  m_axi_sram_RRESP,
    input  var logic        m_axi_sram_RLAST,
    input  var logic        m_axi_sram_RVALID,
    output var logic        m_axi_sram_RREADY
);

    assign m_axi_sram_AWID     = s_axi_spi_AWID;
    assign m_axi_sram_AWADDR   = s_axi_spi_AWADDR;
    assign m_axi_sram_AWLEN    = s_axi_spi_AWLEN;
    assign m_axi_sram_AWSIZE   = s_axi_spi_AWSIZE;
    assign m_axi_sram_AWBURST  = s_axi_spi_AWBURST;
    assign m_axi_sram_AWLOCK   = s_axi_spi_AWLOCK;
    assign m_axi_sram_AWCACHE  = s_axi_spi_AWCACHE;
    assign m_axi_sram_AWPROT   = s_axi_spi_AWPROT;
    assign m_axi_sram_AWREGION = '0;
    assign m_axi_sram_AWVALID  = s_axi_spi_AWVALID;
    assign s_axi_spi_AWREADY   = m_axi_sram_AWREADY;

    assign m_axi_sram_WDATA  = s_axi_spi_WDATA;
    assign m_axi_sram_WSTRB  = s_axi_spi_WSTRB;
    assign m_axi_sram_WLAST  = s_axi_spi_WLAST;
    assign m_axi_sram_WVALID = s_axi_spi_WVALID;
    assign s_axi_spi_WREADY  = m_axi_sram_WREADY;

    assign s_axi_spi_BID     = m_axi_sram_BID;
    assign s_axi_spi_BRESP   = m_axi_sram_BRESP;
    assign s_axi_spi_BVALID  = m_axi_sram_BVALID;
    assign m_axi_sram_BREADY = s_axi_spi_BREADY;

    assign m_axi_sram_ARID     = s_axi_spi_ARID;
    assign m_axi_sram_ARADDR   = s_axi_spi_ARADDR;
    assign m_axi_sram_ARLEN    = s_axi_spi_ARLEN;
    assign m_axi_sram_ARSIZE   = s_axi_spi_ARSIZE;
    assign m_axi_sram_ARBURST  = s_axi_spi_ARBURST;
    assign m_axi_sram_ARLOCK   = s_axi_spi_ARLOCK;
    assign m_axi_sram_ARCACHE  = s_axi_spi_ARCACHE;
    assign m_axi_sram_ARPROT   = s_axi_spi_ARPROT;
    assign m_axi_sram_ARREGION = '0;
    assign m_axi_sram_ARVALID  = s_axi_spi_ARVALID;
    assign s_axi_spi_ARREADY   = m_axi_sram_ARREADY;

    assign s_axi_spi_RID     = m_axi_sram_RID;
    assign s_axi_spi_RDATA   = m_axi_sram_RDATA;
    assign s_axi_spi_RRESP   = m_axi_sram_RRESP;
    assign s_axi_spi_RLAST   = m_axi_sram_RLAST;
    assign s_axi_spi_RVALID  = m_axi_sram_RVALID;
    assign m_axi_sram_RREADY = s_axi_spi_RREADY;

endmodule
