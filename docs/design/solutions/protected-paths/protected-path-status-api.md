# Represent protected paths status in API

## Current implementation
A flow status is an aggregate of all flow paths statuses, including protected paths. It corresponds with "the worst" path status.
For example, if one of the paths has the `DOWN` status, the flow will have the `DOWN` status too.

## Solution
Introduce the `DEGRADED` flow status that will represent a situation when main paths are in `UP` status and protected are not.

Introduce `status_details` section with a separate status for main and protected paths: use the old flow status logic, 
but separately for main and protected path pairs.

Example for get flow with failed protected path:
```
{
    ...
    "allocate_protected_path": true,
    "status": "DEGRADED"
    "status_details": {
        "main_path": UP
        "protected_path": DOWN
    }
}
```
