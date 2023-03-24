# Flow loop feature

## Overview

A Flow loop feature is designed for flow path testing. A loop provides additional flow rules on one of the terminating switches
so that any flow traffic is sent back to switch-port from where it was received. 
Such flow has non-empty `loopSwitchId` property and supports all flow operations. When the loop is removed, the system is able to
restore the original flow rules.

![Flow-loop](flow-loop.png "Flow loop")

## API

* Get existing flow loops: `GET /v2/flows/loops` with optional params `flow_id` and `switch_id`. An example of a response:

```json
[
  {
    "switch_id": "00:00:11:22:33:44:55:66",
    "flow_id": "string"
  },
  ...
]
```


* Create a flow loop on the chosen switch: `POST /v2/flows/{flow_id}/loops` with the body:
```json
{
  "switch_id": "00:00:11:22:33:44:55:66"
}
```

A flow may have only one loop.

* Delete a flow loop and restore regular flow rules `DELETE /v2/flows/{flow_id}/loops`. 

## Details

From persistence perspective, a flow loop is stored as an additional property of a flow node: `loop_switch_id: string`.
This property must be empty for non-looped flows.

Flow loop create and delete operations implemented as a partial update options of the flow update FSM in `flow-hs` topology.
All H&S flow operations use a single point of rule generation based on flow parameters. In current implementation it is
`SpeakerFlowSegmentRequestBuilder` class. It should be modified to support the flow loop flag and reused in flow loop operations.

Additional loop rules have the flow cookie with a special `loop` bit and higher priority. Only these rules are installed or deleted during flow loop operations.

## Additional changes

Flow/switch validation/sync operations use separate expected flow rules generation logic, so it should be changed to support loop rules.

A protected flow may have a loop only on the main path. We need to remove loop rules related to the previous main path and install loop rules for the new main path during path swap.