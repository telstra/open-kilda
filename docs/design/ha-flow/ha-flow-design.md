# High Availability flows (HA-flows)

## Concepts / objects model
Introducing a new Kilda entity - HA-flow. Defined by 3 network endpoints. One "shared" endpoint is defined by
`switch + port + outer-vlan + inner-vlan`. Two other endpoints are defined by `switch + port + outer-vlan + inner-vlan`.
We can name these 3 endpoints as Z-end (the shared endpoint), A-end (the first leaf), B-end (the second leaf).

```
Z-end <===> Y-point <===> A-end
                    <===> B-end
```

There are 2 directions in HA-flows: 
* forward: Z-A and Z-B
* reverse: A-Z and B-Z

HA-flow delivers all traffic from Z-end to A-end and B-end. It meas that 
A-end and B-end will receive identical set of packets from Z-end. 
To reach it OpenFlow group will be installed at Y-point to duplicate forward traffic.
Reverse traffic (from A-end and B-end) will be joined at Y-point and delivered to Z end. 

For traffic control purpose (bandwidth limit): each endpoint (A, B and Z) has an own meter
installed to control traffic. Z-end has a single meter to limit both Z-A and Z-B traffic.
Y-point has a meter to control summary A-end + B-end traffic. 
It means that if HA-flow has bandwidth 1 Mb/s all segments (Z-Y, Y-A, Y-B) will have 
same maximum bandwidth 1 Mb/s in both directions.

HA-flows looks similar to Y-flows, but there is a difference between them.
Y-flow is a composite entity, which consists of 2 Kilda flows ("sub-flows").
But HA-flow is standalone entity which has its own ha-paths with 3 endpoints.

## Behaviour

### Create
Creating behaviour looks like creating of common flow:
1. Validate a request
2. Find paths
3. Allocate resources
4. Install rules, meters, groups

To find 3-end path we can reuse current PCE implementation:
1. Find a path from Z-end to A-end
2. Find a path from Z-end to B-end as close as possible to the first path.

Then determines common portion for them and defines the Y-point where we 
mirror traffic from shared section to each "leaf" and join traffic from each "leaf" to shared endpoint.

If any segment of HA-flow goes down - Kilda marks the whole HA-flow as down and tries to reroute it.   

### Update
Updating behaviour is same to the create operation (except Kilda needs to remove existing HA-flow).

### Patch
Patching behaviour looks like patching of a common flow: request body can contain only fields
which must be updated. But there is one note: as body has a list of sub flows 
(see [API](./ha-flow-nb-api.md)) to update any field of subflow user must specify `flow_id` of this sub flow. 

### Delete
Deleting behaviour looks like deleting of common flow:
1. Validate a request
2. Remove rules.
3. Deallocate resources
4. rules, meters, groups

### Reroute
If Kilda detects (via a network event or flow monitoring) failure of any segment of HA-flow,
a complete HA-flow "reroute" must be triggered. (As in case of common flow).

### Sync / validate
HA-flow can be validated/synced as usual Kilda flow.

### Swap paths
HA-flow can be created with protected paths, which means it has 2 paths: primary and protected. 
Both paths are installed on switches, but only the primary carries the flow traffic.
If Kilda detects a failure on the primary path of any sub-flow, ha-flow paths swap is performed:
all sub-flows of the affected ha-flow move traffic from primary to protected paths.

## Additional docs:
[HA-flow API](./ha-flow-nb-api.md)

[HA switch rules](./ha-flow-switch-rules.md)
