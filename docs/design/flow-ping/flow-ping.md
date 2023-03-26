# Flow pings
## The idea
Use our link validation mechanism to validate the whole flow. We can inject
validation packet on an edge switch. It will be "routed" using flow rules on all 
intermediate switches and cached on the edge switch on other side of flow.
A `PACKET_OUT` message must specify a special out port called "TABLE" to pass
the packet via existing Open Flow rules on that switch.

## Goals
* measure flow latency
* one more way to verify switch availability (detect control plane issues)

# Ping topology
## Periodic pings
Periodic pings are initiated by OpenKilda system periodically with the interval defined by `flow.ping.interval` configuration option.
Pings for both directions for all existing flows are created on each iteration.

If ping succeeds, its latency is saved into OTSDB, in metric `pen.flow.latency` with tags: flowid and direction.

If there are no successful pings for `flow.ping.fail.delay` seconds, a flow failure will be logged and the `PingReport` message 
will be sent into the Kafka topic defined in the `kafka.topic.flow.status` configuration option.

To simplify flow status log filtration, all flow status log messages are prefixed with `{FLOW-PING}` keyword.

An example of `PingReport` message:
```json
{
  "clazz": "org.openkilda.messaging.info.InfoMessage",
  "payload": {
    "clazz": "org.openkilda.messaging.info.flow.FlowPingReport",
    "report": {
      "flowid": "flowping-alpha",
      "status": "OPERATIONAL"
    },
    "timestamp": 1531157435563
  },
  "timestamp": 1531157435563,
  "correlation_id": "435ed47d-2438-4037-b432-d1d0cb01a709"
}
```

![pediodic pings](./periodic-ping-sequence-diagram.png "Periodic ping sequence diagram")

## On demand pings
This kind of flow ping is initiated via NorthBound API. The only mandatory input parameter is the `flowId`. On this request,
kilda will initiate 2 pings: one in the forward direction and one in the reverse direction. 

NorthBound endpoint: URL path `/flows/{flow_id}/ping`, method PUT. You can pass additional ping options in the request body.

Request body example:
```json
{
  "timeout": 2000
}
```

`timeout`: how much time to wait for a response in milliseconds before consider the ping unsuccessful. The default value is 2000 ms.

A successful ping call response:
```json
{
  "flow_id": "flowping-alpha",
  "forward": {
    "ping_success": true,
    "error": null,
    "latency": 5
  },
  "reverse": {
    "ping_success": true,
    "error": null,
    "latency": 2
  },
  "error": null
}
```

A failure in the forward direction:
```json
{
  "flow_id": "flowping-alpha",
  "forward": {
    "ping_success": false,
    "error": "No ping for reasonable time",
    "latency": 0
  },
  "reverse": {
    "ping_success": true,
    "error": null,
    "latency": 1
  },
  "error": null
}
```

![on-demand pings](./on-demand-ping-sequence-diagram.png "On demand ping sequence diagram")

## Common part for all kinds of pings
![common pings](./ping-sequence-diagram.png "All ping kinds common part sequence diagram")
