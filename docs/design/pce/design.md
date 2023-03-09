# Path Computation Engine (PCE)

## Overview

PCE is an OpenKilda module responsible for any network path computations. The main goal is to provide a simple API to calculate all paths required for any system component.

## API

All components should interact with PCE through the PathComputer interface.
PathComputer provides following capabilities:
* Calculate the best path for a flow.

  Criteria for best path computation are dependent on the flow's **path computation strategy** param. For now, two options are available: cost and latency. Only links with the appropriate **flow encapsulation type** and sufficient bandwidth can be used as available network resources. **Ignore bandwidth flag** may be used to turn off the bandwidth limitation. Additionally, **flow group id** param can be used to create [diverse flows](../solutions/pce-diverse-flows/pce-diverse-flows.md).

* Calculate a flow path with the weight lower than the max weight and as close to maxWeight as possible.

  Criteria for path computation with this option are dependent on the flow's **path computation strategy** and **max latency** params. If **max latency** is not specified, or it is equal to zero, then algorithm for computing the best path will be used.

* Calculate path for a flow with ability to reuse provided network resources.

  The same as previous, but available network resources are extended with the list of paths which resources may be reused. 

* Calculate a list of the best available paths

  Criteria for best path computation are dependent on **path computation strategy** param. **Encapsulation type** param is used to calculate available network resources in the same way. Best paths are sorted in descending order of MinAvailableBandwidth.

## Algorithm

Breadth-first search with path weight calculation and path length limitation is used to calculate the best path. 

Starting from a source node, we visit all neighbor nodes and saving paths to all found nodes with the lowest total weight. 

Algorithm for computing the best path (pseudocode):
+ add source node to visit queue
+ for each node in visit queue
    + if node is destination then
        + if path weight is lower than the current best then <br/>
            \* save new path as current best path
        + skip further processing of the current node
    + if current node is already visited, and it's weight is greater than previously found one, then
        + skip further processing of the current node
    + if path to current node is longer then depth limit, or it's weight is greater than current best path weight, then
        + skip further processing of the current node
    + put current node to visited list and save path and weight
    + add all current node neighbors to visit queue with path and weight
+ current best path is the best one
+ if current best path is empty then no path is found

To calculate a path with a weight lower than the max weight and as close to it as possible, a modification of the algorithm for calculating the best path is used. The main change in the algorithm is a shift of zero towards the max weight and finding the value on the left side of zero (and not on the right side as in the first algorithm). Modified algorithm (pseudocode):
+ add source node to visit queue
+ for each node in visit queue
    + if the current path contains node 
        + skip further processing of the current node
    + if the node is a destination then
        + if shifted path weight (`|path weight - max waight|`) is lower than current desired and path weight is lower than the max weight then <br/>
            \* save the new path as the current desired path
        + skip further processing of the current node
    + if path to current node is longer then depth limit, or its weight is greater than the max weight, then
        + skip further processing of the current node
    + if the current node is already visited and shifted weight is greater than the shifted desired path weight in this node then
        + skip further processing of the current node
    + put current node to visited list and save the path and weight
    + add all current node neighbors to visit queue with the path and weight
+ the current desired path is what we were looking for
+ if current desired path is empty then no path is found

### Path computation strategies

Weight function is defined by the path computation strategy.
For now, four strategies are available:
* Cost

A manually assigned value for each link. If a link is under maintenance and/or the link is unstable, then its cost is increased by the preconfigured value.

Weight function:
`Link weight = cost + underMaintenance * underMaintenanceCostRise + unstable * unstableCostRise` where `underMaintenance` and `unstable` are 0 when false and 1 when true.

* Cost and available bandwidth

The same as the Cost strategy, but if two paths have the same cost, then the one with minimal sum of available bandwidths will be chosen.

* Latency

Automatically calculated value for each link. Separate maintenance/unstable penalties is used when calculating the link weight; the function is based on latency in a similar way as for the cost weight function.

Weight function:
`Link weight = latency + underMaintenance * underMaintenanceLatencyRise + unstable * unstableLatencyRise` where `underMaintenance` and `unstable` are 0 when false and 1 when true. Exact value for `underMaintenanceLatencyRise` and `unstableLatencyRise` should be estimated with respect to average and maximal latency in the network. To avoid using unstable links in the best path, it's possible to choose 1 to 10 seconds latency penalties.

`maxLatency` and `maxLatencyTier2` params are used to limit latency for the found path. PCE will return `backUpPathComputationWayUsed = true` flag if the found path has latency greater than `maxLatency`. Flows with such paths should be treated as DEGRADED because the found path violates latency SLA. PCE will not return a path with the latency greater than `maxLatencyTier2`.

* Max latency

This strategy uses the same weight function as the Latency strategy, but uses a modified algorithm to find a path. 
This algorithm finds a path with the latency as close as possible, but less than `maxWeight` param. 
The value of the `maxWeight` parameter is stored in the `maxLatency` field. If a path was not found, 
then the `maxLatencyTier2` field is used as the `maxWeight` parameter.

For more info see [PCE with weights computation strategies](../solutions/pce-weights-strategies/pce-weights-strategies.md).
