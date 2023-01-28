# OpenFlow cookies data

The length of a cookie field is 64. It is divided into several bit groups. Some bit fields are system-wide, i.e. have same meaning for possible cookies. Other bit fields are specific for a specific cookie type (some subset of cookies).

## System wide cookie fields
This is a set of basic fields that are present in all cookie types.

```
S00TTTTT TTTT0000 00000000 00000000 00000000 00000000 00000000 00000000
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* S - SERVICE_FLAG (1 bit): if OF flow marked with 1 in this bit, then it is a service flow.  OpenKilda uses it to manage service traffic: ISL detection, ISL health check, loops prevention, etc.
* T - TYPE_FIELD (9 bit): contains a cookie type code. This type defines the existence and layout of all other bit fields in the cookie.


## Generic cookies (org.openkilda.model.cookie.CookieBase)
This cookie format was the only existing format in OpenKilda from the beginning. It does not put any restrictions on a cookie format, and it can be used to read a "raw" cookie value to determine its actual type.

## Service cookies (org.openkilda.model.cookie.ServiceCookie)
Fields:

```
_00_____ ____0000 00000000 00000000 TTTTTTTT TTTTTTTT TTTTTTTT TTTTTTTT
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* T - SERVICE_TAG_FIELD (32 bits): a unique identifier of a service OF flow

Constraints:
* SERVICE_FLAG == 1
* TYPE_FIELD == SERVICE_OR_FLOW_SEGMENT (0x000)

## Kilda-flow's segment cookies (org.openkilda.model.cookie.FlowSegmentCookie)
Fields:

```
_FR_____ ____LMY0 00000000 00000000 00000000 0000IIII IIIIIIII IIIIIIII
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* I - FLOW_EFFECTIVE_ID_FIELD (20 bits): a unique numeric kilda-flow identifier (equal across all paths)
* R - FLOW_REVERSE_DIRECTION_FLAG (1 bit): if set, OF flow belongs to a reverse (Z-to-A) kilda-flow path
* F - FLOW_FORWARD_DIRECTION_FLAG (1 bit): if set, OF flow belongs to a forward (A-to-Z) kilda-flow path
* L - FLOW_LOOP_FLAG (1 bit): if set, this is a OF flow that "makes" a loop on a kilda-flow 
* M - MIRROR_LOOP_FLAG (1 bit): if set, this is a OF flow that mirrors a kilda-flow
* Y - Y_FLOW_FLAG (1 bit): if set, this is a OF flow that is used by y-flows to share the same meter

Constraints:
* SERVICE_FLAG == 0
* TYPE_FIELD one of {SERVICE_OR_FLOW_SEGMENT(0x000), SERVER_42_FLOW_RTT_INGRESS(0x00C)}
* FLOW_REVERSE_DIRECTION_FLAG or FLOW_FORWARD_DIRECTION_FLAG must be set, but not both of them 

## Kilda-flow's shared segment cookies (org.openkilda.model.cookie.FlowSharedSegmentCookie)
Refers to the OF flows used by several (at least one) kilda-flows.

Fields:
```
_00_____ ____SSSS 00000000 00000000 0000VVVV VVVVVVVV PPPPPPPP PPPPPPPP
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* S - SHARED_TYPE_FIELD (4 bits): shared segment type 
* p - PORT_NUMBER_FIELD (16 bits): a switch port number with the OF flow belongs to this port
* V - VLAN_ID_FIELD (12 bits): vlanId this OF flow matches

Constraints:

* SERVICE_FLAG == 0
* TYPE_FIELD == SHARED_OF_FLOW(0x008)

Possible values of shared segments:
* QINQ_OUTER_VLAN == 0
* SERVER42_QINQ_OUTER_VLAN == 1

## Port colour cookies (org.openkilda.model.cookie.PortColourCookie)
These OF flows cookies are used to mark, or to "colour", switch port's kind in multi-table mode.

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
  SERVER_42_FLOW_RTT_INPUT(0x009),
  SERVER_42_ISL_RTT_INPUT(0x00D)}

## Exclusion cookies (org.openkilda.model.cookie.ExclusionCookie)
Fields:

```
_FR_____ ____0000 00000000 00000000 00000000 0000EEEE EEEEEEEE EEEEEEEE
^                                                                     ^
`- 63 bit                                                             `- 0 bit
```

* E - EXCLUSION_ID_FIELD (20 bits): a unique exclusion numeric identifier 
* R - FLOW_REVERSE_DIRECTION_FLAG (1 bit): refer to a kilda-flow's reverse path (Z-to-A) 
* F - FLOW_FORWARD_DIRECTION_FLAG (1 bit): refer to a kilda-flow's forward path (Z-to-A)

Constraints:
* SERVICE_FLAG == 0
* TYPE_FIELD == EXCLUSION_FLOW(0x00B)
* FLOW_REVERSE_DIRECTION_FLAG or FLOW_FORWARD_DIRECTION_FLAG: either of them must be set, but not both

##Cookies description

| Cookie                  | Name                                      | Description                                                                                                                                                                                                                                                              |
|-------------------------|-------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `0x8000_0000_0000_0001` | `DROP_RULE`                               | Drops all packets.                                                                                                                                                                                                                                                       |
| `0x8000_0000_0000_0002` | `VERIFICATION_BROADCAST_RULE`             | Catches discovery packets and sends them to the controller.                                                                                                                                                                                                              |
| `0x8000_0000_0000_0003` | `VERIFICATION_UNICAST_RULE`               | Catches Vlan ping packets and sends them to the controller.                                                                                                                                                                                                              |
| `0x8000_0000_0000_0004` | `DROP_VERIFICATION_LOOP_RULE`             | Drops uncaught discovery packets.                                                                                                                                                                                                                                        |
| `0x8000_0000_0000_0005` | `CATCH_BFD_RULE`                          | Catches BFD packets.                                                                                                                                                                                                                                                     |
| `0x8000_0000_0000_0006` | `ROUND_TRIP_LATENCY_RULE`                 | Catches round trip packets and sends them to the controller.                                                                                                                                                                                                             |
| `0x8000_0000_0000_0007` | `VERIFICATION_UNICAST_VXLAN_RULE`         | Catches VXLAN ping packets and sends them to the controller.                                                                                                                                                                                                             |
| `0x8000_0000_0000_0008` | `MULTITABLE_PRE_INGRESS_PASS_THROUGH`     | Sends packets from the pre-ingress table to the ingress table.                                                                                                                                                                                                           |
| `0x8000_0000_0000_0009` | `MULTITABLE_INGRESS_DROP`                 | Drops uncaught packets in the ingress table.                                                                                                                                                                                                                             |
| `0x8000_0000_0000_000A` | `MULTITABLE_POST_INGRESS_DROP`            | Drops uncaught packets in the post-ingress table.                                                                                                                                                                                                                        |
| `0x8000_0000_0000_000B` | `MULTITABLE_EGRESS_PASS_THROUGH`          | Sends packets from egress table to the transit table.                                                                                                                                                                                                                    |
| `0x8000_0000_0000_000C` | `MULTITABLE_TRANSIT_DROP`                 | Drops uncaught packets in the transit table.                                                                                                                                                                                                                             |
| `0x8000_0000_0000_000D` | `LLDP_INPUT_PRE_DROP`                     | Sends LLDP packets received from non-ISL/non-Customer ports to the controller.                                                                                                                                                                                           |
| `0x8000_0000_0000_000E` | `LLDP_TRANSIT`                            | Sends LLDP packet from ISL port to the controller.                                                                                                                                                                                                                       |
| `0x8000_0000_0000_000F` | `LLDP_INGRESS`                            | Sends LLDP packets received from customer ports (but not from customer traffic) to the controller.                                                                                                                                                                       |
| `0x8000_0000_0000_0010` | `LLDP_POST_INGRESS`                       | Sends LLDP packets received from customer ports via transit VLAN flows to the controller.                                                                                                                                                                                |
| `0x8000_0000_0000_0011` | `LLDP_POST_INGRESS_VXLAN`                 | Sends LLDP packets received from customer ports via VXLAN flows to the controller.                                                                                                                                                                                       |
| `0x8000_0000_0000_0012` | `LLDP_POST_INGRESS_ONE_SWITCH`            | Sends LLDP packets received from customer ports via one switch flows to the controller.                                                                                                                                                                                  |
| `0x8000_0000_0000_0013` | `ARP_INPUT_PRE_DROP`                      | Sends ARP packets received from on-ISL/non-Customer ports to the controller.                                                                                                                                                                                             |
| `0x8000_0000_0000_0014` | `ARP_TRANSIT`                             | Sends ARP packets from ISL ports to the controller.                                                                                                                                                                                                                      |
| `0x8000_0000_0000_0015` | `ARP_INGRESS`                             | Sends ARP packets received from customer ports (but not from customer traffic) to the controller.                                                                                                                                                                        |
| `0x8000_0000_0000_0016` | `ARP_POST_INGRESS`                        | Sends ARP packets received from customer ports via transit VLAN flows to the controller.                                                                                                                                                                                 |
| `0x8000_0000_0000_0017` | `ARP_POST_INGRESS_VXLAN`                  | Sends ARP packets received from customer ports via VXLAN flows to the controller.                                                                                                                                                                                        |
| `0x8000_0000_0000_0018` | `ARP_POST_INGRESS_ONE_SWITCH`             | Sends ARP packets received from customer ports via one switch flows to the controller.                                                                                                                                                                                   |
| `0x8000_0000_0000_0019` | `SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE`   | Sends flow RTT packet back to server42 for VLAN Flows.                                                                                                                                                                                                                   |
| `0x8000_0000_0000_001A` | `SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE`  | Sends flow RTT packet back to server42 for VXLAN Flows.                                                                                                                                                                                                                  |
| `0x8000_0000_0000_001B` | `SERVER_42_FLOW_RTT_TURNING_COOKIE`       | Catches flow RTT packets, swaps ETH src and dst, and sends them back to IN_PORT.                                                                                                                                                                                         |
| `0x8000_0000_0000_001C` | `SERVER_42_ISL_RTT_OUTPUT_COOKIE`         | Sends ISL RTT packets back to server42.                                                                                                                                                                                                                                  |
| `0x8000_0000_0000_001D` | `SERVER_42_ISL_RTT_TURNING_COOKIE`        | Sends ISL RTT packets back to IN_PORT.                                                                                                                                                                                                                                   |
| `0x8000_0000_0000_001E` | `SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE` | Catches flow RTT packets for VXLAN Flows, swaps ETH src and dst, and sends them back to IN_PORT.                                                                                                                                                                         |
| `0x8000_0000_0000_001F` | `DROP_SLOW_PROTOCOLS_LOOP_COOKIE`         | Catches and drops LACP reply packets if ETH_SRC of this packet is equal to switch ID (the packet returned to the switch which has sent it. It means we have a loop).                                                                                                     |
| `0x8010_0000_XXXX_XXXX` | `LLDP_INPUT_CUSTOMER`                     | Marks LLDP packets from port XXX with metadata.                                                                                                                                                                                                                          |
| `0x8020_0000_XXXX_XXXX` | `MULTI_TABLE_ISL_VLAN_EGRESS`             | Moves VLAN packets received from an ISL port XXX from the input table to the egress table.                                                                                                                                                                               |
| `0x8030_0000_XXXX_XXXX` | `MULTI_TABLE_ISL_VXLAN_EGRESS`            | Moves VXLAN packets received from an ISL port XXX from the input table to the egress table.                                                                                                                                                                              |
| `0x8040_0000_XXXX_XXXX` | `MULTI_TABLE_ISL_VXLAN_TRANSIT`           | Moves VXLAN packets received from an ISL port XXX from the input table to the transit table.                                                                                                                                                                             |
| `0x8050_0000_XXXX_XXXX` | `MULTI_TABLE_INGRESS_RULES`               | Moves packets received from Customer port XXX from the input table to the re-ingress table.                                                                                                                                                                              |
| `0x8060_0000_XXXX_XXXX` | `ARP_INPUT_CUSTOMER_TYPE`                 | Marks ARP packets from port XXX with metadata.                                                                                                                                                                                                                           |
| `0x0080_0000_0YYY_XXXX` | `QINQ_OUTER_VLAN`                         | QinQ rule for matching packets from port XXX by outer VLAN YYY.                                                                                                                                                                                                          |
| `0x0081_0000_0YYY_XXXX` | `SERVER42_QINQ_OUTER_VLAN`                | Server42 QinQ rule for matching packets from port XXX by outer VLAN YYY.                                                                                                                                                                                                 |
| `0x8090_0000_XXXX_XXXX` | `SERVER_42_FLOW_RTT_INPUT`                | Receives a server42 flow RTT packet from port XXX, puts timestamp into packet (if the switch supports it), and move the packet to the pre-ingress table.                                                                                                                 |
| `0x80A0_0000_0000_0000` | `APPLICATION_MIRROR_FLOW`                 | Mirrors traffic for application purposes.                                                                                                                                                                                                                                |
| `0x80D0_0000_XXXX_XXXX` | `SERVER_42_ISL_RTT_INPUT`                 | Forwards server42 ISL RTT packet to ISL port XXX.                                                                                                                                                                                                                        |
| `0x80E0_0000_XXXX_XXXX` | `LACP_REPLY_INPUT`                        | Catches LACP request packets from port XXX, sends it to Floodlight for modification, and then return it back to the port.                                                                                                                                                |
| `0x4000_0000_000X_XXXX` | `INGRESS_FORWARD`                         | Receives Customer packets, push transit encapsulation if needed, and sends it to the port. Path direction is forward. XXX is a path unmasked cookie.                                                                                                                     |
| `0x2000_0000_000X_XXXX` | `INGRESS_REVERSE`                         | Receives Customer packets, push transit encapsulation if needed, and sends it to the port. Path direction is reverse. XXX is a path unmasked cookie.                                                                                                                     |
| `0x4008_0000_000X_XXXX` | `FLOW_LOOP_FORWARD`                       | Makes flow loop for forward direction (sends all customer traffic back to IN_PORT). XXX - path unmasked cookie.                                                                                                                                                          |
| `0x2008_0000_000X_XXXX` | `FLOW_LOOP_REVERSE`                       | Makes flow loop for reverse direction (sends all customer traffic back to IN_PORT). XXX - path unmasked cookie.                                                                                                                                                          |
| `0x4004_0000_000X_XXXX` | `FLOW_MIRROR_FORWARD`                     | Mirrors forward flow traffic to a specific endpoint. XXX is a path unmasked cookie.                                                                                                                                                                                      |
| `0x2004_0000_000X_XXXX` | `FLOW_MIRROR_REVERSE`                     | Mirrors reverse flow traffic to a specific endpoint. XXX is a path unmasked cookie.                                                                                                                                                                                      |
| `0x4002_0000_000X_XXXX` | `FLOW_Y_FORWARD`                          | Y-flow's forward shared meter flow. XXX is a path unmasked cookie.                                                                                                                                                                                                       |
| `0x2002_0000_000X_XXXX` | `FLOW_Y_REVERSE`                          | Y-flow's reverse shared meter flow. XXX is a path unmasked cookie.                                                                                                                                                                                                       |
| `0x40B0_0000_000X_XXXX` | `EXCLUSION_FLOW_FORWARD`                  | Filters packets by 5-tuple for not mirroring them. Forward direction. XXX is an exclusion ID.                                                                                                                                                                            |
| `0x20B0_0000_000X_XXXX` | `EXCLUSION_FLOW_REVERSE`                  | Filters packets by 5-tuple for not mirroring them. Reverse direction. XXX is an exclusion ID.                                                                                                                                                                            |
| `0x40C0_0000_000X_XXXX` | `SERVER_42_FLOW_RTT_INGRESS_FORWARD`      | Receives server42 flow RTT packets from SERVER_42_FLOW_RTT_INPUT, pushes transit encapsulation, and sends them to ISL port. (It's a copy of a regular flow INGRESS_FORWARD rule, but with the matching by server42 input port). XXX is a path unmasked cookie.           |
| `0x20C0_0000_000X_XXXX` | `SERVER_42_FLOW_RTT_INGRESS_REVERSE`      | Receives server42 flow RTT packets from SERVER_42_FLOW_RTT_INPUT, pushes transit encapsulation, and sends them to ISL port. (It's a copy of a regular flow INGRESS_REVERSE rule, but with the matching by server42 input port). XXX is a path unmasked cookie.           |
| `0x4070_0000_YYYX_XXXX` | `STAT_VLAN_FORWARD`                       | Receives Customer packets with a specific VLAN YYY for full port flow and sends them to the ingress table. The path direction is forward. XXX is a path unmasked cookie. This rule is needed to collect statistics about packet's VLANs which go through full port flow. |
| `0x2070_0000_YYYX_XXXX` | `STAT_VLAN_REVERSE`                       | Receives Customer packets with a specific VLAN YYY for full port flow and sends them to the ingress table. The path direction id reverse. XXX is a path unmasked cookie. This rule is needed to collect statistics about packet's VLANs which go through full port flow. |
