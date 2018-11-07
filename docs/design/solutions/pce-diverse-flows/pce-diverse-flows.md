# Ability to create diverse flows

## Goals
Have redundancy on flows: different flows reside on different switches and/or ISLs
Main goal is to find shortest diverse path

## NB contract changes
Request: add flow id to make new flow diverse with

Response: applied diverse path computation strategy for computed diverse path

## DB changes
Create "FlowGroup" new node type in neo4j, that store current diverse strategy.
Flow relation keeps "FlowGroup" id as a property

## Algorithm
Client adds diverse flow id into NB create flow request.
First, system tries to find a diverse path with excluded switches.
If such path doesn't exists, tries to find path with excluded ISLs.
If such path also doesn't exists, tries to find partially diverse path on full network graph.
The applied diverse path computation strategy is persisted and returned to client.

## Reroutes
On reroutes, rerouted path will be suits with at least group diverse strategy, or will be failed.
 
## Sequence Diagram
![Ability to create diverse flows](pce-diverse-flows-create.png)

### Limitations
Flow groups is an implementation detail and there is no API to access it directly.

System knows nothing about physical network topology, so computed paths not truely diverse in hardware meaning
