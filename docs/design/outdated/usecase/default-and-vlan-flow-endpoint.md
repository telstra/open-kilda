# Default and VLAN tagged flow endpoints

## Problem

For now, ether one default flow may exist with untagged traffic routing, or several flows with tagged VLANs, but not both.

## Proposal

Extend system with ability to create flows with the same port, default and VLAN tagged.
 
For default flow, install ingress switch rule with lower priority than VLAN tagged. It will passing port traffic, that is not matched by VLAN specific switch rule (VLAN tagged)
