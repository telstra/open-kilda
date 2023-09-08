# Link Aggregation Control Protocol

## Description

Link Aggregation allows parallel full duplex point-to-point links to be used as if they were a single link.
This design is focused on the following scenario:

![LACP usage scenario](lacp-usage-scenario.png "LACP usage scenario")  

Customer device (Actor) is connected to OpenKilda switch (Partner) by 2 or more physical cables.
All these cables are united into one virtual cable with higher capacity via LACP. 
In this scenario OpenKilda works in passive LACP mode. It means that LACPDU packets must be sent by a customer device, which is in active mode.
To establish LACP a link aggregation connection OpenKilda must only reply on customer LACPDUs.

To be able to work with aggregated links OpenKilda must crate Link Aggregation Group (LAG) on switch physical ports.
OpenKilda LAG provides a logical port which can be used as endpoint port for OpenKilda flows. 
This logical port is an input/output port for a logical group of united physical cables.

## LACP session control

![LACPDU capture](lacpdu-capture.png "LACPDU capture")

Additional openflow rule will be installed for each LAG port on OpenKilda switches to catch LACP packets and send them 
to the controller. This rule has match by `eth_type=0x8809` (Slow protocols), `eth_dst=01:80:C2:00:00:02` (Slow protocols)
and `in_port=<LAG_logical_port>`.
This rule will have a meter to do not overload a controller. All LAG ports on a switch will share one meter.

LACPDU has several fields:
* systemId
* systemPriority
* key
* portNumber
* portPriority
* state

Several links can be aggregated correctly if they have the same systemId and key but different port. 
Open-kilda can aggregate any ports so it should respond with constant system and key but different port.
Port value should be copied from incoming LACPDU to avoid port collisions on partner side. 

Actor state should be determined in the following way:
* active = false (passive LACP)
* timeout copied from incoming packet (fast/slow timeout)
* aggregation = true (all endpoints are aggregeteable by kilda)
* syncronization = true
* collecting copied from incoming packet
* distributing copied from incoming packet (kilda ready to send/receive data as soon as partner system is ready)
* defaulted = false (predefined partner system info isn't supported)
* expired = false

### API 

To enable LACP we will use existing API `POST /v2/{switch_id}/lags`.
The new field `lacp_reply` will be added into request body. This field will be `true` by default. 
`lacp_reply=true` means that OpenKilda will reply on LACP requests.
`lacp_reply=false` means that OpenKilda will ignore LACP requests.

LAG create request:
```json
{
  "port_numbers": [1,2,3],
  "lacp_reply": true
}
```

LAG response:
```json
{
  "logical_port_number": 555,
  "port_numbers": [1,2,3],
  "lacp_reply": true
}
```

To create a flow which will use this LACP a user must set received `logical_port_number` as source or destination port 
in flow create/update request.

### Disadvantages

LACP session is established using control plane. If connectivity is lost for a triple LACP timeout
(3 seconds for fast and 90 seconds for slow mode) all aggregated links on disconnected switch should be marked as 
expired and stop transmit/receive data plane traffic.
