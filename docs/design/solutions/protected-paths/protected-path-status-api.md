# Represent protected paths status in API

## Current implementation
Flow status is an aggregate of all flow paths statuses, including protected paths. It corresponds with "the worst" path status.
For example, if one of the paths is in `DOWN` status, the flow will be in `DOWN` status too.

## Solution
Introduce `DEGRADED` flow status, that will represent situation when main paths are in `UP` status and protected are not.

Introduce `status_details` section, with separate status for main and protected paths: use old flow status logic, but for separate main and protected path pairs.

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
