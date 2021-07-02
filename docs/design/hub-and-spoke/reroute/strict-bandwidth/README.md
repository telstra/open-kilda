# Strict bandwidth flag for flow

## Overview
Now, when the flow cannot fit into the existing bandwidth during a reroute operation,
Reroute topology decides to retry the reroute operation with the `ignore_bandwidth` flag.

## Problem
With this approach, we cannot control which flows will occupy the bandwidth of other flows. 
We need to be able to prevent Reroute topology from setting this flag when retrying the reroute operation.

## Solution
Implement a new `strict_bandwidth` flag for flow that will not allow manipulation 
of the `ignore_bandwidth` flag on retries during the reroute operation.

## Reroute retry result
|         | Actual Latency <= maxLatency (maxLatencyTier2 is not specified) | Actual Latency > maxLatency (maxLatencyTier2 is not specified) | maxLatency < Actual latency <= maxLatencyTier2 | Actual latency > maxLatencyTier2 |
| ---     | ---         | ---      | ---        | ---        |
| **Requested BW is available (`strict_bandwidth = false`)**     | Flow status: Up       | Flow status: Degraded | Flow status: Degraded | Flow status: Down |
| **Requested BW is available (`strict_bandwidth = true`)**      | Flow status: Up       | Flow status: Degraded | Flow status: Degraded | Flow status: Down |
| **Requested BW is not available (`strict_bandwidth = false`)** | Flow status: Degraded | Flow status: Degraded | Flow status: Degraded | Flow status: Down |
| **Requested BW is not available (`strict_bandwidth = true`)**  | Flow status: Down     | Flow status: Down     | Flow status: Down     | Flow status: Down |

## NB contract changes
Create\Update Flow Request: add optional `strict_bandwidth` flag. 
If both the `ignore_bandwidth` and `strict_bandwidth` flags are set in the request, 
we should receive a `BAD_REQUEST` error in the response.

## DB changes
Flow entity keeps `strict_bandwidth` flag as a property.

## Reroute topology changes
If the `strict_bandwidth` flag is set, the `ignore_bandwidth` flag will not be set when retrying the reroute operation.
