# Return diverse flows id in get flow payload 

## Goals
Return flow IDs, that the given flow diverse with in one endpoint.

## Implementation
If a flow is in a group, fetch flows by groupId and return matched flow IDs as `diverse_with` list in the response payload.
