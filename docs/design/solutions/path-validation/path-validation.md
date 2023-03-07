# Path Validation

## Motivation
The main use case is to allow users to verify whether some arbitrary flow path is possible to create with the given 
constraints without actually creating this flow. A path here is a sequence of nodes, that is a sequence of switches
with in and out ports. Constraints are various parameters such as max latency, bandwidth, encapsulation type, 
path computation strategy, and other.

## Implementation details
The validation of a path is done for each segment and each validation type individually. This way, the validation
collects all errors on the path and returns them all in a single response. The response is concise and formed 
in human-readable format.

There is no locking of resources for this path and, therefore, no guarantee that it will be possible to create this flow 
after validation in the future.

## Northbound API

REST URL: ```/v2/network/path/check```, method: ```GET```

A user is required to provide a path represented by a list of nodes. Nodes must be ordered from start to end; 
each next element is the next hop. A user can add optional parameters:
- `encapsulation_type`: enum value "TRANSIT_VLAN" or "VXLAN". API returns an error for every switch in the list if it 
  doesn't support the given encapsulation type.
- `max_bandwidth`: bandwidth required for this path. API returns an error for each segment of the given path when the 
  available bandwidth on the segment is less than the given value. When used in combination with `reuse_flow_resources`,
  available bandwidth as if the given flow doesn't consume any bandwidth.
- `max_latency`: the first tier latency value in milliseconds. API returns an error for each segment of the given path,  
  which max latency is greater than the given value.
- `max_latency_tier2`: the second tier latency value in milliseconds. API returns an error for each segment of the given
  path, which max latency is greater than the given value.
- `path_computation_strategy`: an enum value PathComputationStrategy. API will return different set of errors depending 
  on the selected strategy. For example, when COST is selected API ignores available bandwidth and latency parameters. 
  If none is selected, all validations are executed.  
- `reuse_flow_resources`: a flow ID. Verify the given path as if it is created instead of the existing flow, that is as 
  if the resources of some flow are released before validation. Returns an error if this flow doesn't exist.
- `diverse_with_flow`: a flow ID. Verify whether the given path intersects with the given flow. API returns an error for 
  each common segment. Returns an error if this flow doesn't exist.

### Request Body
```json
{
  "encapsulation_type": "TRANSIT_VLAN|VXLAN",
  "max_bandwidth": 0,
  "max_latency": 0,
  "max_latency_tier2": 0,
  "path_computation_strategy": "COST|LATENCY|MAX_LATENCY|COST_AND_AVAILABLE_BANDWIDTH",
  "reuse_flow_resources": "flow_id",
  "diverse_with_flow": "diverse_flow_id",
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
- is_valid: True if the path is valid and conform to the strategy, false otherwise
- errors: List of strings with error descriptions if any
- correlation_id: Correlation ID from correlation_id Header
```json
{
  "correlation_id": "string",
  "is_valid": "bool",
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

