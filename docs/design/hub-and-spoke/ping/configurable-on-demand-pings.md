# Configurable on-demand pings

## The problem
Current ping implementation covers two cases: regular periodic pings, and single on-demand ping. Ping parameters are either not configurable or system wide.

Current ping implementation https://github.com/telstra/open-kilda/blob/master/docs/design/flow-ping/flow-ping.md

## Use cases
Allow users to do a batch of pings by single API request, with configurable parameters and progress status.

## High-level implementation

###  DAO level
Introduce `ping` node, that is owned by a flow, with ping parameters and current status. Status will be updated with amount of pings or time (configurable).
Node will be present only if there is running ping process.

Node fields:
* Start time
* End time (optional, if time-interval ping)
* Remaining pings count (optional, if count based ping)
* Ping interval
* Ping packet size
* `Do not fragment` flag for ping packet
 
 ### Storm level
Introduce `pingHs` Storm topology, that reworks current on-demand ping workflow with FSM per flow approach, with per-ping options:
* ping interval
* ping count
* test time

Only one ping process per flow may exists.

Also it should provide service interface for start, stop and status operations.

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

Progress stats should periodically flush into DB. Also all ping results should be send into OpenTSDB. 

### Floodlight level
Stays the same, with introducing additional options:
* configurable packet size
* L3 `not fragment` flag