# Return diverse flows id in get flow payload 

## Goals
Return flows id, that flow diverse with in one endpoint

## Implementation
If flow is in a group, fetch flows by groupId and return matched flows id as `diverse_with` list in response payload
