# Configurable on-demand pings

## The problem
Current ping implementation covers two cases: regular periodic pings, and single on-demand ping. Ping parameters are 
either not configurable or system-wide.

Current ping implementation https://github.com/telstra/open-kilda/blob/master/docs/design/flow-ping/flow-ping.md

## Use cases
Allow users to use a single API request for sending multiple pings with configurable parameters and progress status.
Pinging on maximum bandwidth will be implemented in data plane by [Server 42](../../server42/README.md) service.

## High-level implementation

###  DAO level
Introduce a `ping` node, owned by a flow, with ping parameters and current status. The status will be updated with number 
of pings or time (configurable). Node will be present only if there is a running ping process.

Node fields:
* Start time
* End time (optional, if time-interval ping)
* Remaining pings count (optional, if count based ping)
* Ping interval
* Ping packet size
* `Do not fragment` flag for ping packet
 
 ### Storm level
Introduce `pingHs` Storm topology, that reworks the current on-demand ping workflow with FSM per flow approach, with per-ping options:
* ping interval
* ping count
* test time

Only one ping process per flow may exist.

Also, it should provide a service interface for start, stop, and status operations.

Start initiates new ping process. Parameters: 
* Packet size
* Pings count
* Ping test time (in hours)
* Ping interval
* Do not fragment flag

Status returns current ping progress:
* start time
* remaining ping packet count or time
* max/min/average latency
* success/failed ping counters
* extended info about the last ping

Progress stats should be periodically flushed into DB. All ping results should be sent to OpenTSDB.

### Floodlight level
Additional options are introduced:
* configurable packet size (78 bytes minimum with headers, max 9216)
* L3 `not fragment` flag
