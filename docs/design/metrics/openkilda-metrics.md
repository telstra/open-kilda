# OpenKilda metrics

## Introduction
OpenKilda collects statistics about network packets that go through its network.
Module Stats topology is responsible for collecting these statistics. 
All collected metrics go to OpenTSDB. 

## Metric structure
Each metric consists of 3 parts:
1. Name - a metric name which starts with env prefix (see `kilda_opentsdb_metric_prefix`)
2. Value - a numeric value (bits/bytes/packets count, etc)
3. Tags - a set of key-value pairs with some additional information

## Metric types
There are 3 main types of metrics in OpenKilda
1. Flow metrics - information about packets which belong to some specific
flow, Y-flow, or HA-flow. 
2. Service metrics - information about service rules, groups and meters. 
3. Other - all other rules, meters or groups which were installed in the switch.

## Rule metrics
By default, OpenKilda stores statistics about all flow rules using following metrics:
* `<prefix>.flow.raw.bits` - total size of all packets which were processed by a rule in bits
* `<prefix>.flow.raw.bytes` - total size of all packets which were processed by a rule in bytes
* `<prefix>.flow.raw.packets` - total count of packets which were processed by a rule

Each of the metrics above have the following tags:
* `flowid` - String: ID of the flow which a rule belongs, `unknown` if there is no information about a flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `switchid` - String: ID of the switch which rule belongs to
* `cookie` - String: OpenFlow cookie of the rule
* `tableid` - Number: OpenFlow table ID
* `inPort` - Number: input port of a rule (if a rule contains the `in` port in the OpenFlow match section)
* `outPort` - Number: the `out` port of a rule
* `type` - String: rule type
* `is_flow_satellite` - Boolean: `true` if it is a satellite rule (mirror, loop, etc). `false` otherwise.
* `is_mirror` - Boolean: `true` if it is a mirror rule, `false` otherwise. This tag exists only if `is_flow_satellite` is `true`
* `is_loop` - Boolean: `true` if it is a loop rule, `false` otherwise. This tag exists only if `is_flow_satellite` is `true`
* `is_flowrtt_inject` - Boolean: `true` if a rule injects flow RTT timestamp, `false` otherwise. This tag exists only if `is_flow_satellite` is `true`

## Common flow rule metrics
### Common flow ingress rule metrics
* `<prefix>.flow.ingress.bits` - total size of all packets which were processed by flow ingress rule in bits
* `<prefix>.flow.ingress.bytes` - total size of all packets which were processed by flow ingress rule in bytes
* `<prefix>.flow.ingress.packets` - total count of packets which were processed by flows ingress rule

Tags:
* `flowid` - String: ID of the flow which a rule belongs to `unknown` if there is no information about a flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `is_y_flow_subflow` Boolean: `true` if a rule belongs to a flow, which is a sub flow of some Y-flow, `false` otherwise

### Common flow transit rule metrics
Flow transit rules doesn't have some specific metrics, so OpenKilda just stores [raw](#rule-metrics) metrics.

### Common flow egress rule metrics
* `<prefix>.flow.bits` - total size of all packets which were processed by the flow's egress rule in bits
* `<prefix>.flow.bytes` - total size of all packets which were processed by the flow's egress rule in bytes
* `<prefix>.flow.packets` - total count of packets which were processed by the flow's egress rule

Tags:
* `flowid` - String: ID of the flow which a rule belongs, `unknown` if there is no information about a flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `is_y_flow_subflow` Boolean: `true` if a rule belongs to a flow, which is a sub flow of some Y-flow, `false` otherwise

## Y-flow rule metrics
### Y-flow ingress rule metrics
* `<prefix>.flow.ingress.bits` - total size of all packets which were processed by the Y-flow ingress rule in bits
* `<prefix>.flow.ingress.bytes` - total size of all packets which were processed by the Y-flow ingress rule in bytes
* `<prefix>.flow.ingress.packets` - total count of packets which were processed by the Y-flow's ingress rule

Tags:
* `y_flow_id` - String: ID of a Y-flow which a rule belongs to, `unknown` if there is no information about a Y-flow 
* `flowid` - String: ID of a sub flow which a rule belongs to, `unknown` if there is no information about a subflow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `is_y_flow_subflow` Boolean: `true` as a sub flow belongs to a Y-flow

### Y-flow transit rule metrics
Y-flow transit rules doesn't have some specific metrics, so OpenKilda just stores [raw](#rule-metrics) metrics.

### Y-flow egress rule metrics
* `<prefix>.flow.bits` - total size of all packets which were processed by the sub flow's egress rule in bits
* `<prefix>.flow.bytes` - total size of all packets which were processed by the sub flow's egress rule in bytes
* `<prefix>.flow.packets` - total count of packets which were processed by the sub flow's egress rule

Tags:
* `y_flow_id` - String: ID of a Y-flow which a rule belongs to, `unknown` if there is no information about a Y-flow
* `flowid` - String: ID of a sub flow which a rule belongs to, `unknown` if there is no information about a flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `is_y_flow_subflow` Boolean: `true` as a sub flow belongs to a Y-flow

### Y-flow Y point rule metrics
* `<prefix>.yflow.ypoint.bits` - total size of all packets which were processed by the Y-flow's rule in the Y point in bits
* `<prefix>.yflow.ypoint.bytes` - total size of all packets which were processed by the Y-flow's rule in the Y point in bytes
* `<prefix>.yflow.ypoint.packets` - total count of packets which were processed by the Y-flow's rule in the Y point

Tags:
* `y_flow_id` - String: ID of a Y-flow which a rule belongs to, `unknown` if there is no information about a Y-flow
* `flowid` - String: ID of a sub flow which a rule belongs to, `unknown` if there is no information about a flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `is_y_flow_subflow` Boolean: `true` as a sub flow belongs to a Y-flow

## HA-flow rule metrics
By default, OpenKilda stores raw stats for all HA-flow rules (like it does for [common flows rules](#rule-metrics)) 

* `<prefix>.haflow.raw.bits` - total size of all packets which were processed by the HA-flow's rule in bits
* `<prefix>.haflow.raw.bytes` - total size of all packets which were processed by the HA-flow's rule in bytes
* `<prefix>.haflow.raw.packets` - total count of packets which were processed by the HA-flow's rule

Each of the metrics above have the following tags:
* `ha_flow_id` - String: ID of an HA-flow which a rule belongs to, `unknown` if there is no information about an HA-flow
* `flowid` - String: ID of an HA-sub flow which a rule belongs to,
             `shared` if this rule belongs to both sub flows (rule is a part of an HA flow shared path),
             `unknown` if there is no information about a sub flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `switchid` - String: ID of the switch which rule belongs to
* `cookie` - String: OpenFlow cookie of the rule
* `inPort` - Number: input port of a rule (if a rule contains the `in` port in OpenFlow match section)
* `outPort` - Number: the `out` port of a rule
* `tableid` - Number: OpenFlow table ID

### HA-flow ingress rule metrics
* `<prefix>.haflow.ingress.bits` - total size of all packets which were processed by the HA-flow's ingress rule in bits
* `<prefix>.haflow.ingress.bytes` - total size of all packets which were processed by the HA-flow's ingress rule in bytes
* `<prefix>.haflow.ingress.packets` - total count of packets which were processed by the HA-flow's ingress rule

Tags:
* `ha_flow_id` - String: ID of an HA-flow which a rule belongs to, `unknown` if there is no information about an HA-flow
* `flowid` - String: ID of an HA-sub flow which a rule belongs to,
             `shared` if a rule belongs to both sub flows (rule is a part of an HA flow shared path),
             `unknown` if there is no information about a sub flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`

### HA-flow egress rule metrics
* `<prefix>.haflow.bits` - total size of all packets which were processed by the HA-flow's egress rule in bits
* `<prefix>.haflow.bytes` - total size of all packets which were processed by the HA-flow's egress rule in bytes
* `<prefix>.haflow.packets` - total count of packets which were processed by the HA-flow's egress rule

Tags:
* `ha_flow_id` - String: ID of an HA-flow which a rule belongs to, `unknown` if there is no information about an HA-flow
* `flowid` - String: ID of an HA-sub flow which a rule belongs to,
             `shared` if rule belongs to both sub flows (rule is a part of HA flow shared path),
             `unknown` if there is no information about a sub flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`

### HA-flow Y point rule metrics
* `<prefix>.haflow.ypoint.bits` - total size of all packets which were processed by the HA-flow's rule in Y point in bits
* `<prefix>.haflow.ypoint.bytes` - total size of all packets which were processed by the HA-flow's rule in Y point in bytes
* `<prefix>.haflow.ypoint.packets` - total count of packets which were processed by the HA-flow's rule in Y point

* `ha_flow_id` - String: ID of an HA-flow which a rule belongs, `unknown` if there is no information about an HA-flow
* `flowid` - String: ID of an HA-sub flow which a rule belongs to,
             `shared` if rule belongs to both sub flows (rule is a part of HA flow shared path),
             `unknown` if there is no information about a sub flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`

## Meter metrics
> **NOTE:** All meter metrics will have values greater than zero only if traffic's rate will exceed meter's rate
(when a meter starts to drop packets above the meter rate)

## Common flow ingress meter metric
* `<prefix>.flow.meter.bits` - total size of all packets which were dropped by flow meter in bits
* `<prefix>.flow.meter.bytes` - total size of all packets which were dropped by flow meter in bytes
* `<prefix>.flow.meter.packets` - total count of packets which were dropped by flow meter

Tags:
* `switchid` - String: ID of a switch which meter belongs to
* `meterid` - Number: ID of a meter
* `is_y_flow_subflow` Boolean: `false` as a flow doesn't belong to any Y-flow
* `flowid` - String: ID of the flow which a meter belongs, `unknown` if there is no information about a flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `cookie` - String: OpenFlow cookie of a rule which has instruction "go to meter" with the meter id

## Y-flow ingress meter metric
* `<prefix>.flow.meter.bits` - total size of all packets which were dropped by the sub flow's meter in bits
* `<prefix>.flow.meter.bytes` - total size of all packets which were dropped by the sub flow's meter in bytes
* `<prefix>.flow.meter.packets` - total count of packets which were dropped by the sub flow's meter

Tags:
* `switchid` - String: ID of a switch which meter belongs to
* `meterid` - Number: ID of a meter
* `is_y_flow_subflow` Boolean: `true` as a sub flow belong to a Y-flow
* `flowid` - String: ID of the flow which a meter belongs to, `unknown` if there is no information about a flow
* `y_flow_id` - String: ID of a Y-flow which a meter belongs to, `unknown` if there is no information about a Y-flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `cookie` - String: OpenFlow cookie of a rule which has instruction "go to meter" with the meter ID

## Y-flow shared point meter metric
* `<prefix>.yflow.meter.shared.bits` - total size of all packets which were dropped by the shared meter of a Y-flow in bits
* `<prefix>.yflow.meter.shared.bytes` - total size of all packets which were dropped by the shared meter of a Y-flow in bytes
* `<prefix>.yflow.meter.shared.packets` - total count of packets which were dropped by the shared meter of a Y-flow

Tags:
* `switchid` - String: ID of a switch which meter belongs to
* `meterid` - Number: ID of a meter
* `y_flow_id` - String: ID of a Y-flow which a meter belongs, `unknown` if there is no information about a Y-flow

## Y-flow Y point meter metric
* `<prefix>.yflow.meter.ypoint.bits` - total size of all packets which were dropped by the Y-point meter of a Y-flow in bits
* `<prefix>.yflow.meter.ypoint.bytes` - total size of all packets which were dropped by the Y-point meter of a Y-flow in bytes
* `<prefix>.yflow.meter.ypoint.packets` - total count of packets which were dropped by the Y-point meter of a Y-flow

Tags:
* `switchid` - String: ID of a switch which meter belongs to
* `meterid` - Number: ID of a meter
* `y_flow_id` - String: ID of a Y-flow which a meter belongs, `unknown` if there is no information about a Y-flow

## HA-flow meter metrics
### HA-flow ingress meter metrics
* `<prefix>.haflow.meter.bits` - total size of all packets which were dropped by the HA-flow meter in bits
* `<prefix>.haflow.meter.bytes` - total size of all packets which were dropped by the HA-flow meter in bytes
* `<prefix>.haflow.meter.packets` - total count of packets which were dropped by the HA-flow meter

Tags:
* `switchid` - String: ID of a switch which meter belongs to
* `meterid` - Number: ID of a meter
* `ha_flow_id` - String: ID of an HA-flow which a meter belongs to, `unknown` if there is no information about an HA-flow
* `flowid` - String: ID of an HA-sub flow which a meter belongs to,
             `shared` if a rule belongs to both sub flows (meter is a part of HA flow shared path),
             `unknown` if there is no information about a sub flow
* `direction` - String: direction of flow traffic: `forward`, `reverse` or `undefined`
* `cookie` - String: OpenFlow cookie of a rule which has an instruction "go to meter" with the meter id

## HA-flow Y point meter metric
* `<prefix>.haflow.meter.ypoint.bits` - total size of all packets which were dropped by the Y-point meter of an HA-flow in bits
* `<prefix>.haflow.meter.ypoint.bytes` - total size of all packets which were dropped by the Y-point meter of an HA-flow in bytes
* `<prefix>.haflow.meter.ypoint.packets` - total count of packets which were dropped by the Y-point meter of an HA-flow

Tags:
* `switchid` - String: ID of a switch which meter belongs to
* `meterid` - Number: ID of a meter
* `ha_flow_id` - String: ID of a HA-flow which a meter belongs, `unknown` if there is no information about an HA-flow

## Group metrics
## HA-flow metrics
* `<prefix>.haflow.group.ypoint.bits` - total size of all packets which were processed by the Y-point group of an HA-flow meter in bits
* `<prefix>.haflow.group.ypoint.bytes` - total size of all packets which were processed by the Y-point group of an HA-flow in bytes
* `<prefix>.haflow.group.ypoint.packets` - total count of packets which were processed by the Y-point group of an HA-flow

Tags:
* `switchid` - String: ID of a switch which group belongs to
* `groupid` - Number: ID of a meter
* `ha_flow_id` - String: ID of an HA-flow which a group belongs, `unknown` if there is no information about an HA-flow
