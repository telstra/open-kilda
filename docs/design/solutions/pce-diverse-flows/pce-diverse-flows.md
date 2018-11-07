# Ability to create diverse flows

## Goals
Have redundancy on flows: different flows reside on different switches and/or ISLs.
Main goal is to find shortest diverse path with minimal overlapping.

## NB contract changes
Request: add optional flow id, to make new flow diverse with.

Response: diversity counters, for number of intersections by switch and by ISL in diversity flow group.

## DB changes
Flow relation keeps `flow group` id as a property.

## Algorithm
Initial, construct AvailableNetwork as usual.
Then fill AvailableNetwork diversity weights on edges(ISLs) and nodes(switches) what are used by paths in diverse group with some constants, passed as system parameters.

To avoid excessive complexity in weight strategies, propose always add static weights what are filling by additional AvailableNetworkFactory building strategy, like diversity.
Such static weights are either zero in default AvailableNetwork building scenario, or meaningful constant if we explicitly asked to fill them.

The final Edge weight computing formula is: `src sw static weight + edge stretegy result + edge static weight + dest sw static weight`. 

## Intersection count
Responsibility of IntersectionCounter class. Will compute intersection counts from collection of all FlowSegments in flow group. 
Return two numbers, intersection count by ISL and by switch.

## Reroutes
The same logic is used. Reroute will fail only if path not found.
 
## Sequence Diagram
![Ability to create diverse flows](pce-diverse-flows-create.png)

### Limitations
Flow can belongs to only one flow group.

Flow groups is an implementation detail and there is no API to access it directly.

System knows nothing about physical network topology, so computed paths not truly diverse in hardware meaning.
