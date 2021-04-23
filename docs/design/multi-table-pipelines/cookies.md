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
