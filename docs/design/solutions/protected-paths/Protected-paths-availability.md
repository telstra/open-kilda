# Protected paths availability

## Goal
Provide a read-only operation that allows to know whether a path between selected switches could have a protected path. 

### Use case
Assume we have the following topology of switches connected to each other with links with the certain costs:
```
A-------5----B----1--------C
 \           |            /
  \          1           /
   \         |          /
    ----1----D----5-----
```
Suppose we want to know whether it is possible to create a flow between A and C with a protected path. The answer 
depends on the specific parameters of the flow. If we say that the flow path cost must be at most 3, then PCE will find
only one path A-D-C, which cannot have a protected path in this topology. If the cost is at most 6, then PCE will find 
4 paths: A-B-C and A-D-C could have protected paths, while A-D-B-C and A-B-D-C could not.   

## Proposal
Extend `network/paths` API so that each found path contains a field saying whether there is at least one possible path 
that is fully diverse from the given one. To enable this option, in V2 API, there will be a field in the request body 
(other optional parameters are omitted):
```json
{
  "src_switch": "00:00:00:00:00:00:00:01",
  "dst_switch": "00:00:00:00:00:00:00:02",
  "protected" : true
}
```
In V1 API, there will be a URL parameter: 
```
protected=true
```

OpenKilda will find all paths between the given switches and return the response:
```json
{
  "paths": [
    {
      "bandwidth": 0,
      "is_backup_path": true,
      "latency": 0,
      "latency_ms": 0,
      "latency_ns": 0,
      "is_protected_path_available": true,
      "nodes": [
        {
          "input_port": 0,
          "output_port": 0,
          "switch_id": "string"
        }
      ]
    }
  ]
}
```
The field `is_protected_path_available` is true iff there is at least one diverse path for the given path. 
The implementation will reuse the existing code in that finds paths and the availability check of the protected path is
done reusing PCE capabilities.
Since there are no any locking and resource allocation. This API cannot guarantee that it will be possible to create 
a flow with the protected path in the future.
