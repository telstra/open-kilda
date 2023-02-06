# Path Validation

## Motivation
The main use case is to allow users to validate if some arbitrary flow path is possible to create with the given 
constraints without actually creating this flow. A path here is a sequence of nodes, that is a sequence of switches
with in and out ports. Constraints are various parameters such as max latency, bandwidth, encapsulation type, 
path computation strategy, and other.

## Implementation proposal
Reuse PCE components (specifically extend AvailableNetwork) for the path validation in the same manner as it is used for
the path computation. Use nb worker topology, PathService, and PathsBolt because the path validation is read-only.

_An alternative approach is to use FlowValidationHubBolt and the resource manager. This might help to mitigate concurrency 
issues related to changes in repositories during a path validation process._

There is no locking of resources for this path and, therefore, no guarantee that it will be possible to create this flow 
after validation in the future.

## Northbound API

REST URL: ```/v2/network/validate-path```, method: ```GET```

A user is required to provide a path represented by a list of nodes. Nodes must be ordered from start to end, 
each next element is the next hop. A user can add optional parameters.

### Request Body
```json
{
  "encapsulation_type": "TRANSIT_VLAN|VXLAN",
  "max_latency": 0,
  "max_latency_tier2": 0,
  "path_computation_strategy": "COST|LATENCY|MAX_LATENCY|COST_AND_AVAILABLE_BANDWIDTH",
  "nodes": [
    {
      "switch_id": "00:00:00:00:00:00:00:01",
      "output_port": 0
    },
    {
      "switch_id": "00:00:00:00:00:00:00:02",
      "input_port": 0,
      "output_port": 1
    },
    {
      "switch_id": "00:00:00:00:00:00:00:03",
      "input_port": 0
    }
  ]
}
```

### Responses

Code: 200

Content:
- valid: True if the path is valid and conform to strategy, false otherwise
- errors: List of strings with error descriptions if any
- correlation_id: Correlation ID from correlation_id Header
```json
{
  "correlation_id": "string",
  "valid": "bool",
  "errors": [
    "error0",
    "errorN"
  ]
}
```

Codes: 4XX,5XX
```json
{
  "correlation_id": "string",
  "error-description": "string",
  "error-message": "string",
  "error-type": "string",
  "timestamp": 0
}
```

