# Ability to create diverse flows

## Goals
Have redundancy on flows: different flows reside on different switches and/or ISLs.
The main goal is to find the shortest diverse path with a minimal overlapping.

## NB contract changes
Create\Update Flow Request: add an optional field that contains the flow ID, to make the flow diverse with.

Get Flow Path Response: if a flow belongs to a diversity group, the response additionally returns other paths in the group with intersection statistics.

## DB changes
Diverse group ID is as a property of a flow.

## Algorithm
Construct AvailableNetwork as usual. Then fill AvailableNetwork diversity weights on edges (ISLs) and nodes (switches) 
that are used by paths in a diverse group with some constants passed as system parameters.

To avoid excessive complexity in weight strategies, it is proposed to always add static weights that are filled by 
AvailableNetworkFactory. These static weights are either zero in default AvailableNetwork building scenario, or a
constant value, if a target flow has "diversity group" property.

The final Edge weight computing formulas are: 
- `edge src sw static weight + edge strategy result + edge static weight + edge dest sw static weight` for the first Edge in the path.
- `edge strategy result + edge static weight + edge dest sw static weight` for the rest Edges in the path.

## Reroutes
The same logic is used - diversity weights will be filled if a rerouting flow has the "diverse group" property.
Reroute will fail only if path not found.

## Limitations
A flow can belong to only one diversity group.

Flow groups is an implementation detail and there is no API to access it directly.

System knows nothing about physical network topology, so computed paths not truly diverse from hardware perspective.

In the current implementation, affinity and diverse groups cannot be specified at the same time for the same flow.
([Affinity flows](../pce-affinity-flows/pce-affinity-flows.md))

## Sequence Diagram
![Ability to create diverse flows](pce-diverse-flows-create.png)

### Flow Path Response schema (with diversity group paths)
```
{
  "flowid": "flow1",
  "flowpath_forward": [],
  "flowpath_reverse": [],
  "diverse_group": {
    // overlapping between flow1 path and other group paths
    "overlapping_segments": {
      "isl_count": 0,
      "switch_count": 2,
      "isl_percent": 0,
      "switch_percent": 100
    },
    // foreach other flow in group
    "other_flows": [
      {
        "flowid": "other1",
        "flowpath_forward": [],
        "flowpath_reverse": [],
        // overlapping between flow1 path and other1 path
        "overlapping_segments": {
          "isl_count": 0,
          "switch_count": 1,
          "isl_percent": 0,
          "switch_percent": 50
        }
      }
    ]
  }
}
```
