# HA-flow rules

## A,B,Z endpoint

In case of HA flow endpoints looks like common flow endpoint so its rules will do the same:
* match be port and 2 vlans
* apply meter
* push into port

## Transit switch 
Transit rules look the same as common flow transit rules 

### Y-point

Y point must:
1. Duplicate traffic from Z-end
2. Join and limit traffic from A-end and B-end

For purpose number 1 we need to add an OF group for traffic duplication in forward direction.
For purpose number 2 we need to add 2 rules (each for matching packets from A-end and B-end) which uses 
common meter to limit joined traffic.
