# Ability to create diverse flows

## Goals
Have redundancy on flows: different flows reside on different switches and/or ISLs
Main goal is to find shortest diverse path

## NB contract changes
Request: add optional flow id, to make new flow diverse with

Response: applied diverse path computation strategy for computed diverse path

## DB changes
Create "FlowGroup" new node type in neo4j.
Flow relation keeps "FlowGroup" id as a property

## Algorithm
Construct AvailableNetwork as usual. Then update costs on ISL and switches what are used by paths in diverse group by some constants, passed in system parameters.

## Intersection count
Responsibility of IntersectionCounter class. Will compute from collection of all FlowSegments in FlowGroup. Return two numbers, intersection count by ISL and by switch.

## Reroutes
The same logic is used. Reroute will fail only if path not found, intersection counts after reroute saving in group.
 
## Sequence Diagram
![Ability to create diverse flows](pce-diverse-flows-create.png)

### Limitations
Flow groups is an implementation detail and there is no API to access it directly.

System knows nothing about physical network topology, so computed paths not truely diverse in hardware meaning
