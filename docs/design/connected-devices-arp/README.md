# Switch connected devices via ARP 

## Summary

This feature allows to detect devices connected to a particular switch
by catching ARP packets. Design of the feature is based on multi-table feature design and
connected devices via LLDP feature design.

## API

A new boolean field `switch_arp` will be added to switch properties.
After setting it to `true` by API `PUT /{switch-id}/properties`, several rules will be installed on the switch.
You can find descriptions of these rules in the section [Detecting of connected devices via ARP](#detecting-connected-devices-via-arp).

Due to the switch ARP feature is based on multi-table feature user can't set `switch_arp` property to `true`
without setting `multi_table` to `true`. Bad request will be returned otherwise.

V2 API `GET /{switch_id}/devices?since={time}` will be updated.

A new field `arp` will be added to each port entity. This field will contain a list of devices
connected to a specific port which were detected by ARP packets.

Each device will have the following fields:

* `vlan`
* `macAddress`
* `ipAddress`
* `timeFirstSeen`
* `timeLastSeen`

Each device will have an optional field `flowId`. 
This field will be set if an ARP packet belongs to a specific flow. 

Updated API response will look like:

~~~json
{
  "ports": [
      {
         "port_number": int,
         "lldp": [{
            "vlan": int,
            "macAddress": string,
            "chassisId": string,
            "portId": string,
            "ttl": int,
            "portDescription": string,
            "systemName": string,
            "systemDescription": string,
            "systemCapabilities": string,
            "managementAddress": string,
            "flowId": string,
            "timeFirstSeen": string,
            "timeLastSeen": string
           },
           ***
         ],
         "arp": [{
            "vlan": int,
            "macAddress": string,
            "ipAddress": string,
            "flowId": string,
            "timeFirstSeen": string,
            "timeLastSeen": string
           },
           *** 
         ]
      },
      ***
   ]
}
~~~

## Detecting connected devices via ARP

To detect connected devices via ARP we will catch ARP packets from each switch port
and send them to the controller to analyze them in Connected Devices Storm Topology.

There are three types of switch ports:
1. Customer (port with created flow on it).
2. ISL
3. Other (port without ISL and without flows).
 
Rules will be different for each port type.

Full description of new rules you can find in
[this](https://drive.google.com/file/d/1keTueyvh6iDtRhO5XXxgMBV0rZFbgPoL/view?usp=sharing) doc.

Short description:

![Catching of ARP customer](arp_catching_customer.png "Catching of ARP customer")

![Catching of ARP isl](arp_catching_isl.png "Catching of ARP isl")

![Catching of ARP other](arp_catching_other.png "Catching of ARP other")

## Switch rules

There will be introduced 7 new rules, grouped in the following types:
* Flow (1 rule for each flow and 3 default rules)
* Isl (1 default rule)
* Other (2 default rule)

#### Flow rules

A rule for each flow in the Input Table:

![Input Flow](input_flow.png "Input Flow")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_TYPE = 0x0806 (ARP eth type) 
        OFPXMT_OFB_IN_PORT = XXX (flow input port) 
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_WERITE_METADATA]
                    value = 0x0004 (mark packet as ARP)
                    mask = 0x0004
                [OFPAT_GO_TO_TABLE]
                    table_id = 2 (ingress table)

```

A rule for one switch flows in the Post Ingress table:

![One Switch Flow](one_switch_flow.png "One Switch Flow")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_METADATA = 0x0006 (packet marked as ARP and as One Switch Flow)
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535
```

A rule for VXLAN flows in the Post Ingress table:

![VXLAN Flow](vxlan_flow.png "VXLAN Flow")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_METADATA = 0x0004 (packet marked as ARP)
        OFPXMT_OFB_IP_PROTO = UDP
        OFPXMT_OFB_UDP_SRC = vlan udp src
        OFPXMT_OFB_UDP_DST = vlan udp dst
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_POP_TUNELL_ID] (pop vxlan)
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535
```

A rule for other flows in the Post Ingress table:

![Vlan Flow](vlan_flow.png "Vlan Flow")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_METADATA = 0x0004 (packet marked as ARP)
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535
```

#### ISL rules

One rule in the Transit table:

![ISL rule](isl_rule.png "ISL rule")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_TYPE = 0x0806 (ARP eth type) 
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535
```

#### Other rule

One rule in the Input Table:

![Input_Drop Rule](input_drop.png "Input Drop Rule")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_TYPE = 0x0806 (ARP eth type) 
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535

```

One rule in the Ingress Table to catch ARP from customer ports (only packets which are not belong to any flow):

![Ingress_Drop Rule](ingress_drop.png "Ingress Drop Rule")

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_TYPE = 0x0806 (ARP eth type) 
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535

```
