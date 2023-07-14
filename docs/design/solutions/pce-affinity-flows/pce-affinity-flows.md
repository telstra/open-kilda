# Affinity flows

## Goals
The main goal is to be able to find a path that maximally repeat the path of a certain main flow.

## NB contract changes
Create\Update Flow Request: add an optional field that contains the ID of the affinity group this flow belongs to.

Flow Response: If a flow is in an affinity group, the flow response will contain an `affinity_with` field 
with the main flow ID of that group.

## DB changes
Affinity group ID is a property of a flow.

## Path computation algorithm changes
In PCE, flow affinity works similar to diversity groups. During available network computation, edges, that are
not a part of the affinity flow path, have an additional penalty. Thanks to that penalty edges from the affinity flow
path are more likely to be selected and the resulting path reuses as many segments of the main flow as possible.

### Use cases
1. An affinity flow has the path 1-2-3. We need to build a path from the node 1 to 4. There are three available paths: 
1-4, 1-2-4, 1-2-3-4. Since they all have only one link unused by main path they will have the same affinity penalty
and the path with minimal total weight will be chosen. If we have equal initial weights for all these links we will 
use path 1-4 because it's total weight is minimal.

![Use case 1](use-case-1.png)
 
2. An affinity flow has the path 1-2-3-4. We need to build path from the node 1 to 7. A lot of paths are available, but only two
of them has one link unused by the main path: 1-2-3-7 and 1-2-3-4-7. All other paths will have more links unused by the 
main path and, thus, will have greater affinity penalty. If we have equal initial weights for all these links, we will 
use the path 1-2-3-7 because it's total weight is minimal.     

![Use case 2](use-case-2.png)

3. An affinity flow has the path 1-2-5. We need to build a path from the node 3 to 6. Two paths are available: 3-2-5-6 
and 3-4-7-6. The first path reuses one link from the main flow, so it has lesser affinity penalty and should be used.

![Use case 3](use-case-3.png)

## Limitations
 - A flow can only belong to one flow affinity group.

 - Flow affinity groups are an implementation detail and there is no API to access it directly.

 - In the current implementation, affinity and diverse groups can not be specified at the same time for one flow.
   (see [diverse flows](../pce-diverse-flows/pce-diverse-flows.md) for details)

 - In current implementation, affinity groups don't support protected flows because protected flows are implemented using diversity feature.

## Sequence Diagram
![Create affinity flows](pce-affinity-flows-create.png)

