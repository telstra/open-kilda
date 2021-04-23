# OpenFlow cookies data

Cookie filed is 64 bits long, it is divided into several bit groups. Some bit fields are system wide i.e. have same meaning for possible cookies, other bit fields are specific for specific cookie type (some subset of cookies).

## System wide cookie fields
This is set of basic fields that presents in all cookie types.

```
S00TTTTT TTTT0000 00000000 00000000 00000000 00000000 00000000 00000000
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* S - SERVICE_FLAG (1 bit) - OF flow marked with 1 in this bit is a service flow, required for open-kilda to manage service traffic (ISL detection / health check, loops prevention etc)
* T - TYPE_FIELD (9 bit) - contain cookie type code, this type defines existence and layout all other bit fields in the cookie


## Generic cookies (org.openkilda.model.cookie.CookieBase)
This cookie format was the only existing format into open-kilda from its start. It is not put any restrictions on cookie format and can be used to read "raw" cookie value to determine its actual type.

## Service cookies (org.openkilda.model.cookie.ServiceCookie)
Fields:

```
_00_____ ____0000 00000000 00000000 TTTTTTTT TTTTTTTT TTTTTTTT TTTTTTTT
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* T - SERVICE_TAG_FIELD (32 bits) - unique identifier of service OF flow

Constraints:
* SERVICE_FLAG == 1
* TYPE_FIELD == SERVICE_OR_FLOW_SEGMENT (0x000)

## Kilda-flow's segment cookies (org.openkilda.model.cookie.FlowSegmentCookie)
Fields:

```
_FR_____ ____L000 00000000 00000000 00000000 0000IIII IIIIIIII IIIIIIII
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* I - FLOW_EFFECTIVE_ID_FIELD (20 bits) - unique numeric kilda-flow identifier (equal across all path)
* R - FLOW_REVERSE_DIRECTION_FLAG (1 bit) - if set OF flow belongs to reverse (Z-to-A) kilda-flow path
* F - FLOW_FORWARD_DIRECTION_FLAG (1 bit) - if set OF flow belongs to forward (A-to-Z) kilda-flow path
* L - FLOW_LOOP_FLAG (1 bit) - if set this is OF flow that "makes" loop on kilda-flow 

Constraints:
* SERVICE_FLAG == 0
* TYPE_FIELD one of {SERVICE_OR_FLOW_SEGMENT(0x000), SERVER_42_INGRESS(0x00C)}
* FLOW_REVERSE_DIRECTION_FLAG or FLOW_FORWARD_DIRECTION_FLAG must be set, but not both of them 

## Kilda-flow's shared segment cookies (org.openkilda.model.cookie.FlowSharedSegmentCookie)
Refers to the OF flows used by several (at least one) kilda-flows.

Fields:
```
_00_____ ____SSSS 00000000 00000000 0000VVVV VVVVVVVV PPPPPPPP PPPPPPPP
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* S - SHARED_TYPE_FIELD (4 bits) - shared segment type 
* p - PORT_NUMBER_FIELD (16 bits) - number of switch port with OF flow belongs to
* V - VLAN_ID_FIELD (12 bits) - vlanId this OF flow matches

Constraints:

* SERVICE_FLAG == 0
* TYPE_FIELD == SHARED_OF_FLOW(0x008)

Possible values of shared segments:
* QINQ_OUTER_VLAN == 0

## Port colour cookies (org.openkilda.model.cookie.PortColourCookie)
OF flows used to "colour" switches port's kind in multi-table mode.

Fields:
```
_00_____ ____0000 00000000 00000000 PPPPPPPP PPPPPPPP PPPPPPPP PPPPPPPP
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* P - PORT_FIELD (32 bits) - port number

Constraints:
* SERVICE_FLAG == 1
* TYPE_FIELD one of {
  LLDP_INPUT_CUSTOMER_TYPE(0x001), 
  MULTI_TABLE_ISL_VLAN_EGRESS_RULES(0x002), 
  MULTI_TABLE_ISL_VXLAN_EGRESS_RULES(0x003), 
  MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES(0x004), 
  MULTI_TABLE_INGRESS_RULES(0x005), 
  ARP_INPUT_CUSTOMER_TYPE(0x006), 
  SERVER_42_INPUT(0x009)}

## Exclusion cookies (org.openkilda.model.cookie.ExclusionCookie)
Fields:

```
_FR_____ ____0000 00000000 00000000 00000000 0000EEEE EEEEEEEE EEEEEEEE
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* E - EXCLUSION_ID_FIELD (20 bits) - unique exclusion numeric identifier 
* R - FLOW_REVERSE_DIRECTION_FLAG (1 bit) - refer to kilda-flow's reverse path (Z-to-A) 
* F - FLOW_FORWARD_DIRECTION_FLAG (1 bit) - refer to kilda-flow's forward path (Z-to-A)

Constraints:
* SERVICE_FLAG == 0
* TYPE_FIELD == EXCLUSION_FLOW(0x00B)
* FLOW_REVERSE_DIRECTION_FLAG or FLOW_FORWARD_DIRECTION_FLAG must be set, but not both of them

##Cookies description

|Cookie|Name|Description|
|------|----|-----------|
|`0x8000_0000_0000_0001`|`DROP_RULE`|Drops all packets|
|`0x8000_0000_0000_0002`|`VERIFICATION_BROADCAST_RULE`|Catches discovery packets and sends them to controller|
|`0x8000_0000_0000_0003`|`VERIFICATION_UNICAST_RULE`|Catches Vlan ping packets and sends them to controller|
|`0x8000_0000_0000_0004`|`DROP_VERIFICATION_LOOP_RULE`|Drops uncatched discovery packets|
|`0x8000_0000_0000_0005`|`CATCH_BFD_RULE`|Cathces BFD packets|
|`0x8000_0000_0000_0006`|`ROUND_TRIP_LATENCY_RULE`|Catches round trip packets and sends them to controller|
|`0x8000_0000_0000_0007`|`VERIFICATION_UNICAST_VXLAN_RULE`|Catches VXLAN ping packets and sends them to controller|
|`0x8000_0000_0000_0008`|`MULTITABLE_PRE_INGRESS_PASS_THROUGH`|Sends packets from pre-ingress table to ingress table|
|`0x8000_0000_0000_0009`|`MULTITABLE_INGRESS_DROP`|Drops uncatched packets in ingress table|
|`0x8000_0000_0000_000A`|`MULTITABLE_POST_INGRESS_DROP`|Drops uncatched packets in post-ingress table|
|`0x8000_0000_0000_000B`|`MULTITABLE_EGRESS_PASS_THROUGH`|Sends packets from egress table to transit table|
|`0x8000_0000_0000_000C`|`MULTITABLE_TRANSIT_DROP`|Drops uncatched packets in transit table|
|`0x8000_0000_0000_000D`|`LLDP_INPUT_PRE_DROP`|Sends LLDP packets received from not ISL/Customer ports to controller|
|`0x8000_0000_0000_000E`|`LLDP_TRANSIT`|Sends LLDP packet from ISL port to controller|
|`0x8000_0000_0000_000F`|`LLDP_INGRESS`|Sends LLDP packets received from customer ports (but not from customer traffic) to controller|
|`0x8000_0000_0000_0010`|`LLDP_POST_INGRESS`|Sends LLDP packets received from customer ports via transit vlan flows to controller|
|`0x8000_0000_0000_0011`|`LLDP_POST_INGRESS_VXLAN`|Sends LLDP packets received from customer ports via VXLAN flows to controller|
|`0x8000_0000_0000_0012`|`LLDP_POST_INGRESS_ONE_SWITCH`|Sends LLDP packets received from customer ports via one switch flows to controller|
|`0x8000_0000_0000_0013`|`ARP_INPUT_PRE_DROP`|Sends ARP packets received from not ISL/Customer ports to controller|
|`0x8000_0000_0000_0014`|`ARP_TRANSIT`|Sends ARP packet from ISL port to controller|
|`0x8000_0000_0000_0015`|`ARP_INGRESS`|Sends ARP packets received from customer ports (but not from customer traffic) to controller|
|`0x8000_0000_0000_0016`|`ARP_POST_INGRESS`|Sends ARP packets received from customer ports via transit vlan flows to controller|
|`0x8000_0000_0000_0017`|`ARP_POST_INGRESS_VXLAN`|Sends ARP packets received from customer ports via VXLAN flows to controller|
|`0x8000_0000_0000_0018`|`ARP_POST_INGRESS_ONE_SWITCH`|Sends ARP packets received from customer ports via one switch flows to controller|
|`0x8000_0000_0000_0019`|`SERVER_42_OUTPUT_VLAN`|Sends flow RTT packet back to server42 for Vlan Flows|
|`0x8000_0000_0000_001A`|`SERVER_42_OUTPUT_VXLAN`|Sends flow RTT packet back to server42 for VXLAN Flows|
|`0x8000_0000_0000_001B`|`SERVER_42_TURNING`|Catches flow RTT packer, swaps ETH src and dst and sends back to IN_PORT|
|`0x8010_0000_XXXX_XXXX`|`LLDP_INPUT_CUSTOMER`|Marks LLDP packets from port XXX by metadata|
|`0x8020_0000_XXXX_XXXX`|`MULTI_TABLE_ISL_VLAN_EGRESS`|Moves Vlan packets received from ISL port XXX from input table to egress table|
|`0x8030_0000_XXXX_XXXX`|`MULTI_TABLE_ISL_VXLAN_EGRESS`|Moves VXLAN packets received from ISL port XXX from input table to egress table|
|`0x8040_0000_XXXX_XXXX`|`MULTI_TABLE_ISL_VXLAN_TRANSIT`|Moves VXLAN packets received from ISL port XXX from input table to transit table|
|`0x8050_0000_XXXX_XXXX`|`MULTI_TABLE_INGRESS_RULES`|Moves packets received from Customer port XXX from input table to pre-ingress table|
|`0x8060_0000_XXXX_XXXX`|`ARP_INPUT_CUSTOMER_TYPE`|Marks ARP packets from port XXX by metadata|
|`0x0080_0000_0YYY_XXXX`|`QINQ_OUTER_VLAN`|QinQ rule for matching packets from port XXX by outer VLAN YYY|
|`0x8090_0000_XXXX_XXXX`|`SERVER_42_INPUT`|Receives server42 flow RTT packet from port XXX, puts timestamp into packet (if switch has such support) and move packet to pre-ingress table|
|`0x80A0_0000_0000_0000`|`APPLICATION_MIRROR_FLOW`|Flow mirror traffic for application purposes.|
|`0x4000_0000_000X_XXXX`|`INGRESS_FORWARD`|Receives Customer packets, push transit encapsulation if needed and sends to port. Path direction - forward, XXX - path unmasked cookie|
|`0x2000_0000_000X_XXXX`|`INGRESS_REVERSE`|Receives Customer packets, push transit encapsulation if needed and sends to port. Path direction - reverse, XXX - path unmasked cookie|
|`0x4008_0000_000X_XXXX`|`FLOW_LOOP_FORWARD`|Makes flow loop for forward direction (sends all customer traffic back to IN_PORT). XXX - path unmasked cookie|
|`0x2008_0000_000X_XXXX`|`FLOW_LOOP_REVERSE`|Makes flow loop for reverse direction (sends all customer traffic back to IN_PORT). XXX - path unmasked cookie|
|`0x40B0_0000_000X_XXXX`|`EXCLUSION_FLOW_FORWARD`|Filter packets by 5-tuple to not mirror it. Forward direction. XXX - exlusion ID|
|`0x20B0_0000_000X_XXXX`|`EXCLUSION_FLOW_REVERSE`|Filter packets by 5-tuple to not mirror it. Reverse direction. XXX - exlusion ID|
|`0x40C0_0000_000X_XXXX`|`SERVER_42_INGRESS_FORWARD`|Receives server42 flow RTT packet from SERVER_42_INPUT, push transit encapsulation and sends to ISL port. (It's a copy of regular flow INGRESS_FORWARD rule but with matching by server42 input port). XXX - path unmasked cookie|
|`0x20C0_0000_000X_XXXX`|`SERVER_42_INGRESS_REVERSE`|Receives server42 flow RTT packet from SERVER_42_INPUT, push transit encapsulation and sends to ISL port. (It's a copy of regular flow INGRESS_REVERSE rule but with matching by server42 input port). XXX - path unmasked cookie|
