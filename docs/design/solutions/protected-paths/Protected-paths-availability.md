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
only one path A-D-C, which cannot have a protected path in this topology. If the cost is at most 11, then PCE will find 
4 paths: A-B-C and A-D-C could have protected paths, while A-D-B-C and A-B-D-C could not.   

## Proposal
Extend `network/paths` API so that each found path contains a field containing an object representing a protected path.
If a response doesn't contain any path, it means that PCE cannot find a protected path in the given context. Or, in other 
words, if a field with a protected path is absent or empty, it is not possible to create a flow with a protected path.

In V1 API, there will be a URL parameter: 
```
includeProtectedPath=true
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
      "protected_path": {
        "bandwidth": 0,
        "is_backup_path": true,
        "latency": 0,
        "latency_ms": 0,
        "latency_ns": 0,
        "nodes": [
          {
            "input_port": 0,
            "output_port": 0,
            "switch_id": "string"
          }
        ]
      },
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
The field `protected_path` contains the best protected path in the given context, or an empty object if PCE is unable 
to find any protected path.
The implementation of this feature will reuse the existing code that finds paths, so that it is future compatible 
with the changes in PCE or get paths functionality.
Since there are no any locking and resource allocation, this API cannot guarantee that it will be possible to create 
a flow with the particular protected path in the future.

### Backward compatibility and consistency
When a user don't use the new parameter ```includeProtectedPath=true```, then the response doesn't contain any new fields from this 
feature.  
