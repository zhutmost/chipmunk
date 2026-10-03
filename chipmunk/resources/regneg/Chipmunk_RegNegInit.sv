`default_nettype none

module Chipmunk_RegNegInit #(
  parameter int               WIDTH       = 1,
  parameter bit               RESET_ASYNC = 1'b1,
  parameter logic [WIDTH-1:0] INIT = '0
)(
  input  var logic             clock,
  input  var logic             reset,
  input  var logic             en,
  input  var logic [WIDTH-1:0] d,
  output var logic [WIDTH-1:0] q
);
  logic [WIDTH-1:0] r;
  assign q = r;

  generate
    if (RESET_ASYNC) begin: g_async
      always_ff @(negedge clock, posedge reset) begin
        if (reset)   r <= INIT;
        else if (en) r <= d;
      end
    end else begin: g_sync
      always_ff @(negedge clock) begin
        if (reset)   r <= INIT;
        else if (en) r <= d;
      end
    end
  endgenerate

endmodule: Chipmunk_RegNegInit

`default_nettype wire
