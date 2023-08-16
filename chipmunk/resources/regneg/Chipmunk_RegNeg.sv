`default_nettype none

module Chipmunk_RegNeg #(
  parameter int WIDTH = 1
)(
  input  var logic             clock,
  input  var logic             en,
  input  var logic [WIDTH-1:0] d,
  output var logic [WIDTH-1:0] q
);
  logic [WIDTH-1:0] r;
  assign q = r;

  always_ff @(negedge clock) begin
    if (en) r <= d;
  end

endmodule: Chipmunk_RegNeg
