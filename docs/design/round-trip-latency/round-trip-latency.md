# Round trip latency

## Current implementation

In current implementation we measure latency from Switch A to Switch B by discovery packets.
![Current latency implementation](current_discovery.png "Current latency implementation")

## Round trip latency design with NoviFlow switches

The new way to measure round trip latency:

1) Send a discovery as a packet_out to switch A
2) Switch A put a timestamp t1 to the discovery packet and send it to switch B
3) Switch B receives the discovery packet and does 2 things: 
    * send it to the controller
    * modify packet (UDP port) and send it back to switch A (via group table)
4) Switch A receives his own message, add a timestamp t2 to it and send it to a controller.
5) Controller use the discovery packet from Switch B for ISL discovery purposes and store ISL in DB (without latency).
6) Controller use the discovery packet from Switch A for a roundtrip latency (t2-t1) measurement purposes:
    * Save ISL latency in DB
    * Send latency stats to OTSDB 

![Round trip latency design](new_discovery_noviflow_with_groups_support.png "Round trip latency design")

## Round trip latency design with NOT NoviFlow switch

Not NoviFlow switches can't store timestamps so the old way of latency measuring will be used for them.
![Round trip latency design for NOW noviflow switches](new_discovery_not_noviflow.png "Round trip latency design for NOT noviflow switches")

## Decision maker service

Important part of IslLatency topology is decision maker service. This service decides what we should do with one way
latency from Discovery packets. It's important for boundary cases.
Even if one of two switches doesn't supports all needed features we can calculate round trip latency.
Following table will help you to understand how it works. IslLatency topology is deci

**NOTE:** 
* SC - Source switch supports Copy Field action
* SG - Source switch supports needed group actions
* DC - Destination switch supports Copy Field action
* DG - Destination switch supports needed group actions
* RTL - Round trip latency
* If switch supports Copy Field action, it supports needed group actions too.


SC | SG | DC | DG | Action | Explanation
---|----|----|----|--------|------------
+  |+   |+   |+   |Drop discovery packet|Both switches supports all needed features. It means both of them will receive RTL packets.
+  |+   |-   |+   |Copy RTL from reverse ISL|Src switch supports Copy Field action. Dst switch doesn't supports Copy Field action and can't put a timestamp into packet. But Dst switch supports Group action. It means that Src switch will receive RTL packet and store RTL in DB. We can just copy this latency from reverse ISL.|
-  |+   |+   |+   |Drop discovery packet|Dst switch supports Copy Field action. Src switch supports group action. It means that Dst switch will receive RTL packet somewhen. Discovery packet is useless.
-  |-   |+   |+   |Use one way latency|Src switch doesn't supports both features. Src and Dst switches will never receive RTL packet. Have to use One way latency from Discovery packet
+  |+   |-   |-   |Use one way latency|Dst switch doesn't supports both features. Both switches will never receive RTL packet. Have to use one way latency from Discovery packet.
-  |?   |-   |?   |Use one way latency|Both switches don't support Copy Field action and will never receive RTL packet. Have to use one way latency from Discovery packet.
