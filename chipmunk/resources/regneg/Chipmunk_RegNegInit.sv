`default_nettype none

module Chipmunk_RegNegInit #(
  parameter int    WIDTH       = 1,
  parameter string RESET_ASYNC = "true"
)(
  input  var logic             clock,
  input  var logic             reset,
  input  var logic             en,
  input  var logic [WIDTH-1:0] init,
  input  var logic [WIDTH-1:0] d,
  output var logic [WIDTH-1:0] q
);
  logic [WIDTH-1:0] r;
  assign q = r;

  if (RESET_ASYNC == "true") begin: g_reset_async
    always_ff @(negedge clock, posedge reset) begin
      if (reset)   r <= init;
      else if (en) r <= d;
    end
  end else if (RESET_ASYNC == "false") begin: g_reset_sync
    always_ff @(negedge clock) begin
      if (reset)   r <= init;
      else if (en) r <= d;
    end
  end

endmodule: Chipmunk_RegNegInit
