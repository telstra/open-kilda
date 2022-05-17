# Y-flows

## Concepts / objects model
Introducing a new Kilda entity - Y-flow. Defined by 3 network endpoints. One "shared" endpoint is defined by
`switch + port + outer-vlan + inner-vlan-A + inner-vlan-B`. Two other endpoints are defined by `switch + port + vlan` (can match or can
differ from `inner-vlan-A/B`). We can name these 3 endpoints as Z-end (the shared endpoint), A-end (the first leaf), B-end (the second
leaf).

Y-flow is a composite entity, it consists of 2 Kilda flows ("sub-flows"). They are almost identical to existing Kilda flows, but they can
share ingress/egress endpoint and allocated bandwidth. There's also a Y-point which indicates where Y-flow split.

```
Z-end <===> Y-point <===> A-end
                    <===> B-end
```

For traffic control purpose (bandwidth limit): each leaf endpoint (A-end, B-end) has a dedicated meter installed 
to control traffic from leaf side. Z-end has a single meter to limit both sub-flows as there is only one input.
Y-point has a similar meter to control summary A-end + B-end traffic.

## Behaviour

### Create
At first, Kilda creates 2 sub-flows `Z-end ==> A-end` and `Z-end ==> B-end`, and their paths are as close as possible. 
Then determines common portion for them and defines the Y-point where we split traffic from shared section to each "leaf".

Once both sub-flows are created and the Y-point is defined, Kilda installs required meters to limit summary traffic bandwidth.

Y-flow status is calculated from sub-flow's statuses. If all paths (`Z-end ==> A-end` and `Z-end ==> B-end`) are UP - the whole Y-flow is UP. 
In other cases it can be some kind degraded or DOWN.

### Update
Update behaviour is same to the create behaviour (except Kilda needs to remove existing Y-flow).

### Delete
Mark all sub-flows to remove, and remove them one by one.

### Reroute
If Kilda detects (via a network event or flow monitoring) failure of any sub-flow, a complete Y-flow "reroute" must be triggered:
reroute the failed sub-flow(s), recalculate the Y-point and reinstall Y-flow meters.

Sub-flows of a Y-flow are rerouted in the same way as usual Kilda flows. After reroute any component of Y-flow,
Y-flow's status must be recalculated.

### Sync / validate
Each flow included into Y-flow can be validated/synced as usual Kilda flow.

### Swap paths
A y-flow can be created with protected paths, which means each sub-flow has 2 paths: primary and protected. 
Both paths are installed on switches, but only the primary carries the flow traffic.
If Kilda detects a failure on the primary path of any sub-flow, y-flow paths swap is performed:
all sub-flows of the affected y-flow move traffic from primary to protected paths.

## Prerequisite
1. PCE should allow building overlapped paths for sub-flows - their paths are as close as possible. 
2. Kilda should allow sharing the allocated bandwidth among sub-flows - to avoid double allocation on common part of the paths.
