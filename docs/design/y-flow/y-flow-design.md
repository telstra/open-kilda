# Y-flows

## Concepts / objects model
Introducing a new OpenKilda entity - Y-flow. It is defined by 3 network endpoints. One "shared" endpoint is defined by
`switch + port + outer-vlan + inner-vlan-A + inner-vlan-B`. Two other endpoints are defined by `switch + port + vlan` (can match or can
differ from `inner-vlan-A/B`). We can name these 3 endpoints as Z-end (the shared endpoint), A-end (the first leaf), B-end (the second
leaf).

Y-flow is a composite entity. It consists of 2 OpenKilda flows ("sub-flows"). They are almost identical to the existing Kilda flows, but they can
share ingress/egress endpoint and allocated bandwidth. There's also a Y-point which indicates where Y-flow split.

```
Z-end <===> Y-point <===> A-end
                    <===> B-end
```

For traffic control purpose, such as bandwidth limit, each leaf endpoint (A-end, B-end) has an installed dedicated meter  
to control traffic from leaf side. Z-end has a single meter to limit both sub-flows as there is only one input.
Y-point has a similar meter to control summary A-end + B-end traffic.

## Behaviour

### Create
At first, OpenKilda creates 2 sub-flows `Z-end ==> A-end` and `Z-end ==> B-end`, and their paths are as close as possible. 
Then OpenKilda determines a common portion for them and defines the Y-point where we split traffic from shared section to each "leaf".

Once both sub-flows are created and the Y-point is defined, OpenKilda installs required meters to limit summary traffic bandwidth.

Y-flow status is calculated from sub-flow's statuses. If all paths (`Z-end ==> A-end` and `Z-end ==> B-end`) are UP, the whole Y-flow is UP. 
Otherwise, it can be `DEGRADED` or `DOWN`.

### Update
Update behaviour is the same as the creation behaviour, except OpenKilda removes the existing Y-flow and then proceeds with creation.

### Delete
OpenKilda marks all sub-flows to remove and remove them one by one.

### Reroute
If OpenKilda detects (via a network event or flow monitoring) a failure of any sub-flow, a complete Y-flow "reroute" procedure must be triggered:
reroute the failed sub-flows, recalculate the Y-point, and reinstall Y-flow meters.

Sub-flows of a Y-flow are rerouted in the same way as the usual OpenKilda flows. After rerouting any component of a Y-flow,
Y-flow's status must be recalculated.

### Sync / validate
Each flow included into Y-flow can be validated/synced as usual OpenKilda flow.

### Swap paths
A Y-flow can be created with protected paths, which means each sub-flow has 2 paths: primary and protected. 
Both paths are installed on switches, but normally only the primary carries the flow traffic.
If OpenKilda detects a failure on the primary path of any sub-flow, y-flow paths swap is performed:
all sub-flows of the affected Y-flow move traffic from primary to protected paths.

## Prerequisite
1. PCE should allow building overlapped paths for sub-flows: their paths are as close as possible. 
2. OpenKilda should allow sharing the allocated bandwidth among sub-flows to avoid double allocation on common parts of the paths.
