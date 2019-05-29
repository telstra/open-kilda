# Full port and Vlan tagged ports as flow endpoint

## Problem

For now, only one flow may exist with full port endpoint, or several flows with tagged Vlans port endpoints, but not both.

## Proposal

Extend system with ability to create flows with the same endpoint port, full port and Vlan tagged.
 
For full port endpoint, install ingress switch rule with lower priority than Vlan tagged. It will passing port traffic, that is not matched by Vlan specific switch rule (Vlan tagged endpoint)
