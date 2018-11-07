# Ability to create diverse flows

## Goals
Have redundancy on flows: different flows reside on different switches and/or ISLs.
Main goal is to find shortest diverse path with minimal overlapping.

## NB contract changes
Request: add optional flow id, to make new flow diverse with.

Response: diversity counters, for number of intersections by switch and by ISL in diversity FlowGroup.

## DB changes
Create "FlowGroup" new node type in neo4j.
Flow relation keeps "FlowGroup" id as a property.

## Algorithm
Initial, construct AvailableNetwork as usual.
Then fill AvailableNetwork diversity weights on edges(ISLs) and nodes(switches) what are used by paths in diverse group with some constants, passed as system parameters.
Use weight strategy `src sw diverse weight + edge cost + edge diverse weight + dest sw diverse weight` for final weights computing. 

## Intersection count
Responsibility of IntersectionCounter class. Will compute intersection counts from collection of all FlowSegments in FlowGroup. 
Return two numbers, intersection count by ISL and by switch.

## Reroutes
The same logic is used. Reroute will fail only if path not found. Will update intersection counts in FlowGroup after reroute.
 
## Sequence Diagram
![Ability to create diverse flows](pce-diverse-flows-create.png)

### Limitations
Flow can belongs to only one FlowGroup.

Flow groups is an implementation detail and there is no API to access it directly.

System knows nothing about physical network topology, so computed paths not truely diverse in hardware meaning
