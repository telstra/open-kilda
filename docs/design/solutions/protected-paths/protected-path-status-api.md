# Represent protected paths status in API

## Current implementation
Flow status is an aggregate of all flow paths statuses, including protected paths. It corresponds with "the worst" path status.
For example, if one of the paths is in `DOWN` status, the flow will be in `DOWN` status too.

## Solution one
Separate flow status for main and protected paths: keep current flow status as an aggregate for main path pair,
and introduce additional flow status for protected path pair.

Example for get flow with failed protected path:
```
{
    ...
    "allocate_protected_path": true,
    "status": "UP",
    "protected_path_status": "DOWN"
}
```

## Solution two
Introduce `DEGRADED` flow status, that will represent situation when main paths are in `UP` status and protected are not.

Example for get flow with failed protected path:
```
{
    ...
    "allocate_protected_path": true,
    "status": "DEGRADED"
}
```
