# Round Trip ISL Ping With Latency 

## Goal

Have ability to ping ISL from one end to another and back with round trip latency measurement.  

## Kilda OF rules

There are 3 new OF rules will be added:
* Input Rule - forwards a ping packet from Server 42 port to the ISL port.
* Turning Rule - sends a ping packet from the opposite switch back to the origin.
* Output Rule - sends a ping packet back to Server 42.

## Input Rule

Quantity: One rule per ISL port on a switch<br/>
Table: Input

Purposes:
* Match a ping packet from Server 42 port.
* Set `UDP_SRC` to `4710` to mark the packet as forward ping.
* Send the packet to the ISL port. 

```
[FLOW_ID1]
    TableId     = 0
    Priority    = 24574
    Cookie      = 80D0_0000_0000_0XXX               (offset + the ISL port)
    [MATCHFIELDS]
        OFPXMT_OFB_IN_PORT  = XX                    (Server 42 port)
        OFPXMT_OFB_ETH_DST  = XX:XX:XX:XX:XX:XX     (the switch MAC)
        OFPXMT_OFB_ETH_TYPE = 0x800                 (IP4)
        OFPXMT_OFB_IP_PROTO = 17                    (UDP)
        OFPXMT_OFB_UDP_SRC  = 10XXX                 (offset + the ISL port)
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
            [ACTIONS]
                [OFPAT_SET_FIELD]
                    udp_src = 4710                  (RTT forward UDP port)
                [OFPAT_SET_FIELD]
                    et_dst  = ZZ:ZZ:ZZ:ZZ:ZZ:ZZ     (the special ISL RTT "magic" MAC)
                [OFPAT_OUTPUT]
                    port    = YYY                   (the ISL port)
```

## Turning Rule

Quantity: One rule per a switch<br/>
Table: Input

Purposes of this rule are:
* Match a forward ping packet from another switch.
* Set `UDP_SRC` equal to `4711` to mark a packet as reverse ping.
* Send the packet back to `IN_PORT`.

 ```
[FLOW_ID2]
    TableId     = 0
    Priority    = 31768
    Cookie      = 8000_0000_0000_001D
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_DST  = ZZ:ZZ:ZZ:ZZ:ZZ:ZZ     (the special ISL RTT "magic" MAC)
        OFPXMT_OFB_ETH_TYPE = 0x800                 (IP4)
        OFPXMT_OFB_IP_PROTO = 17                    (UDP)
        OFPXMT_OFB_UDP_SRC  = 4710                  (RTT forward UDP port)
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
            [ACTIONS]
                [OFPAT_SET_FIELD]
                    udp_src = 4711                  (RTT reverse UDP port)
                [OFPAT_OUTPUT]					
                    port    = IN_PORT
 ```
 
## Output Rule
Quantity: One rule per a switch<br/>
Table: Input

Purposes of this rule are:
* Match a reverse ping packet from another switch.
* Send the packet to Server 42 port.

```
[FLOW_ID3]
    TableId     = 0
    Priority    = 31768
    Cookie      = 8000_0000_0000_001C
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_DST  = ZZ:ZZ:ZZ:ZZ:ZZ:ZZ     (the special ISL RTT "magic" MAC)
        OFPXMT_OFB_ETH_TYPE = 0x800                 (IP4)
        OFPXMT_OFB_IP_PROTO = 17                    (UDP)
        OFPXMT_OFB_UDP_SRC  = 4711                  (RTT reverse UDP port)
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
            [ACTIONS]
                [OFPAT_PUSH_VLAN]
                    vlan    = YYYY                  (Server 42 vlan)
                [OFPAT_SET_FIELD]
                    eth_src = XX:XX:XX:XX:XX:XX     (the switch MAC)
                [OFPAT_SET_FIELD]
                    eth_dst = YY:YY:YY:YY:YY:YY     (Server 42 MAC)
                [OFPAT_OUTPUT]
                    port    = XX                    (Server 42 port)
```
