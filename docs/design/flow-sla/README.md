# SLA for flows in Kilda

## Problem

Since the first release there were only one SLA for Kilda flows - bandwidth. But now there are more sla's that are taken
into account for the flows. E.g. latency, latency_tier2. The purpose of this doc is to rethink and redesign how these 
SLAs are visible outside the system and define strategy for adding new ones.

## Current State

Here is the list of flow fields that are taken into account by path computer:
- maximum bandwidth
- max_latency
- max_latency_tier2 
- diverse_flow_id
- affinity_flow_id
- allocate_protected_path
- ignore_bandwidth
- strict_bandwidth

Planned to add:
- max_bandwidth_tier2 

All these fields are part of flow entity, which is not helpful from operational perspective and also isn't practical
from visibility perspective.

## Proposal

Introduce new Entity in API and Core layer which will group all SLA related fields for the object. Add new API, which
increase visibility of the flow SLA's status

### Entity:

`FlowSla` with the following list of fields:
- bandwidth_tier1
- bandwidth_tier2
- latency_tier1
- latency_tier2
- affinity_flow_id
- diversity_flow_id
- protected_path
- strict_bandwidth


## API

added new field to flow entity in crud operations

### POST / PUT / PATCH  
```
{
  "flow_id": "string",
  ...
  "sla": {
    "bandwidth_tier1": 0,
    "bandwidth_tier2": 0,
    "latency_tier1": 0, 
    "latency_tier2": 0,
    "affinity_flow_id": "string",
    "diversity_flow_id": "string",
    "protected_path": true,
    "strict_bandwidth": true,
  }
  ...
}
```

Note: flag `ignore_bandwitdh` is deprecated and is replaced with `strict_bandwidth`
Note: if operator is required a flow that isn't consume bandwith at all `bandwidth_tier_1` should be set to 0,

If new and old fields are provided by the requests, the `sla` fields will be used. 

### GET

The actual flow sla should be available via get endpoint. New sla statuses should extend status_details object of a flow
```
"status": "string",
  "status_details": {
    "sla_state": {
      "bandwidth_tier1": true,
      "bandwidth_tier2": true,
      "latency_tier1": true, 
      "latency_tier2": true,
      "affinity_flow_id": true,
      "diversity_flow_id": true,
      "protected_path": true,
      "strict_bandwidth": true,
    }
    "main_path": "string",
    "protected_path": "string"
  },
  "status_info": "string",
```



