# Y-flows

## Concepts / objects model

New kilda entity - Y-flow. Defined by 3 network endpoint. One "shared" endpoint defined by
switch+port+outer-vlan+inner-vlan-A+inner-vlan-B. Two other endpoints defined by switch+port+vlan (can match or can
differ from inner-vlan-A/B). We can name these 3 endpoints as Z-end(shared endpoint), A-end (firs leaf), B-end (second
leaf).

Y-flow is composite entity, it consists of 3 kilda-flow. They are almost identical to existing kilda-flows, but they can
share ingress/egress endpoint with other flows (so we can join them somewhere in network and route traffic from one to
another).

Traffic control (bandwidth limit) - on Z-end we can use simple meter (there is only one input there), same as existing
kilda-flows. On each leaf endpoints (A-end, B-end) we must install meter, to control traffic from leaf side. Also, we
need meter in Y-point to control summary A-end + B-end traffic.

```
Z-end <===> Y-point <===> A-end
                    <===> B-end
```

## Behaviour

### Create

Need to locate/define joint-point (Y-point) where we will split traffic from shared section to each "leaf". Most simple
solution build Z-end -> A-end on full length (in this case Y-point -> A-end become one switch flow), and make separate
Y-point -> B-end part. (Z-end ===> (Y-point)A-end ===> B-end).

Another option - calculate 2 paths Z-end ==> A-end AND Z-end ==> B-end, determine common portion for them, use this
common portion as Z-end ==> Y-point flow. THis approach will require many new features in PCE.

When we have Y-point we can define 3 flow that represent Y-flow and create them using our existing toolset.

Y-flow status - status calculated from flow's statuses included into Y-flow. If at least one of the full paths (Z-end
==> Y-point + Y-point ==> A-end of Z-end ==> Y-point + Y-point ==> Z-end) is UP - whole Y-flow is UP. In other cases it
can be some kind degraded or DOWN.

Components that will require modifications:
* flow validation (in flow-hs), need to allow interference of specific flow endpoints
* FL-command (and commands factory) - need to route traffic from one kilda-flow to another
* OF-flows/pipeline - need to unwrap transit encapsulation, match with vlan-tag inside it, and route to another flow (
  wrap new transit encapsulation and route to ISL or transform packet into client-view and output into specific port).

### Update
Update behaviour is same to the create behaviour (except we need to remove existing Y-flow).

### Delete
Mark all nested flows to remove, and remove them one by one.

### Reroute

All parts of Y-flow should be rerouted in the same way as usual kilda-flows. After reroute any component of Y-flow,
Y-flow's status must be recalculated.

If we detect (status after reroute / server42 flow status) failure of Z-end <===> Y-point portion of Y-flow we must emit
complete Y-flow "reroute" - recalculate Y-point and recreate nested kilda-flows. 

### Sync / validate
Each flow included into Y-flow can be validated/synced as usual kilda-flow.


## Prerequisite

Rework packet processing inside OF-switch - need to split processing on 2 parts - first match and colour packet, second
match colour and route. 

It will allow to decouple packet's origin from our reaction on it. For example, we can get packet
from customer, from server42 or from other kilda-flow, but all these packets must be processed in same way. So we have 3
different match rule/OF-flow that write some identifier into metadata and one rule/OF-flow that match packet by metadata
and apply required actions.
