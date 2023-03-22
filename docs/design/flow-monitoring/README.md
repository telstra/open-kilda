# Flow SLA monitoring and reactions

Open-kilda should detect in real-time a flow which violates latency check, defined in the service level agreement (SLA).
A new topology `flow-monitor` is designed for this purpose. 

## Flow SLA monitoring
The main idea is to keep an in-memory cache of all flows with actual latencies and a cache of ISLs with actual latencies. 

Flow cache structure:

| Flow id  | Server42 latency | Flow path  |
|----------|------------------|------------|

All flows should pass SLA checks periodically. If a flow has not expired Server42 latency measurement then we can do a simple check.
In another case, we should calculate actual flow latency based on used ISLs latencies with priority to RTT latency.
When we need to calculate ISL latencies, FlowCacheBolt creates a request with a new correlation-id and sends this request with 
the ID to IslCacheBolt for each required ISL. FlowCacheBolt sums up latencies from the responses and waits until it receives
the required number (number of ISLs in flow path) of responses with the given ID. When all required responses are received,
the FlowCacheBolt sends the calculated latency to the ActionBolt.

ISL cache structure:

| ISL | last RTT latency | RTT latency expiration time | last one way latency |
|-----|------------------|-----------------------------|----------------------|

![Flow-monitoring-topology](flow-monitoring-topology.png "Flow monitoring")

On the topology startup both caches are initialized with data from the database. During the topology's lifetime, the system events
help to keep caches up-to-date. `flow-hs` will notify `flow-monitor` about any flow path changes using additional events.
All flow-modifying FSMs will execute `NOTIFY_FLOW_MONITOR` action before the finish. `network` topology will notify `flow-monitor`
about ISL changes.

![Isl-cache-update](isl-cache-update.png "Isl cache update")
![Flow-cache-update](flow-cache-update.png "Flow cache update")
![Flow-latency-check](flow-latency-check.png "Flow latency check")

## Flow SLA reactions

Every calculated flow latency value will be sent to the `stats` topology and saved in openTSDB.

Flow SLA reactions are applied only to flows with a latency-based path computation strategy. Flow has two levels of latency:
SLA called max_latency and max_latency_tier_2. When the actual flow latency is lower than the max_latency, this flow is considered `UP`.
When the latency is between the max_latency and max_latency_tier_2, flow is considered `DEGRADED`. Otherwise, flow is considered `DOWN`.
When latency is fluctuating around one of the SLA levels, a timeout between checks is used to prevent flow status flapping.
The system will change the actual flow status only if the flow latency stably exceeds the SLA level during a specified time window. 
![Flow-latency-monitoring](flow-latency-monitoring.png "Flow latency monitoring")

Flows with zero or null max_latency value are excluded from the monitoring. Flows with a valid max_latency value but with
null or zero max_latency_tier_2 act like max_latency_tier_2 is infinity. So, they may go to the `DEGRADED` status but 
cannot go to the `DOWN` status based on the latency.

Auto reroute is triggered by the system when flow changes status to `DEGRADED` or `DOWN` only when a flow has latency-based
path computation strategy. `DEGRADED` status for a flow may be caused by different reasons. When a flow is moved from 
`DEGRADED` state to `UP`, the flow sync is required to determine the actual flow status. The flow latency FSM doesn't change
flow status directly. Only the flow reroute or the sync, triggered by monitoring, is able to change the flow status.

The current flow latency will be stored in DB and updated periodically or every time the flow latency changes its status.

A finite state machine with the following structure will be used to cover the logic described above.
Every flow latency measurement produces one of the following events: `healthy`, `tier1failed`, or `tier2failed`. Latency value,
`maxLatency`, `maxLatencyTier2`, threshold value, and the last event type are used to determine which event to produce as
shown on the following picture:
![Flow-latency-events](flow-latency-events.png "Flow latency events")

When the flow latency FSM detects a stable state, it produces a corresponding event: `stable-healthy`, `stable-tier1failed`, or `stable-tier2failed`.
Then, it sends a required reroute/sync request.

![Flow-latency-fsm](flow-latency-fsm.png "Flow latency fsm")