# Path Computation Engine (PCE)

## Overview

PCE is a open-kilda module responsible for any network path computations. The main goal is to provide simple API to calculate all paths required for any system component.

## API

All components should interact with PCE through PathComputer interface.
PathComputer provides following options:
* Calculate best path for flow

  Criteria for best path computation is dependent on flow **path computation strategy** param. For now two options are available: cost and latency. Only links with appropriate **flow encapsulation type** and sufficient bandwidth can be used as available network resources. **Ignore bandwidth flag** may be used to turn off bandwidth limitation. Additionally **flow group id** param can be used to create [diverse flows](../solutions/pce-diverse-flows/pce-diverse-flows.md).

* Calculate flow path with a weight less than max weight and as close to maxWeight as possible.

  Criteria for path computation with this option is dependent on flow **path computation strategy** and **max latency** params. If **max latency** is not specified or it is equal to zero, then algorithm for computing best path will be used.

* Calculate path for flow with ability to reuse provided network resources

  The same as previous but available network resources are extended with the list of paths which resources may be reused. 

* Calculate list of best available paths

  Criteria for best path computation is dependent on **path computation strategy** param. **Encapsulation type** param is used to calculate available network resources in the same way. Best paths are sorted in descending order of MinAvailableBandwidth.

## Algorithm

Breadth-first search with path weight calculation and path length limitation is used to calculate best path. 

Starting from source node we visit all neighbor nodes and saving path to all found nodes with lowest total weight. 

Algorithm for computing best path (pseudocode):
+ add source node to visit queue
+ for each node in visit queue
    + if node is destination then
        + if path weight is lower then current best then <br/>
            \* save new path as current best path
        + skip further processing of the current node
    + if current node is already visited and it's weight is greater then previously found one then
        + skip further processing of the current node
    + if path to current node is longer then depth limit or it's weight is greater then current best path weight then
        + skip further processing of the current node
    + put current node to visited list and save path and weight
    + add all current node neighbors to visit queue with path and weight
+ current best path is the best one
+ if current best path is empty then no path is found

To calculate a path with a weight less than max weight and as close to it as possible, a modification of the algorithm for calculating the best path is used. The main change in the algorithm is a shift of zero towards max weight and finding the value on the left side of zero (and not on the right side as in the first algorithm). Modified algorithm (pseudocode):
+ add source node to visit queue
+ for each node in visit queue
    + if current path contains node 
        + skip further processing of the current node
    + if node is destination then
        + if shifted path weight (`|path weight - max waight|`) is lower then current desired and path weight is lower then max weight then <br/>
            \* save new path as current desired path
        + skip further processing of the current node
    + if path to current node is longer then depth limit or it's weight is greater then max weight then
        + skip further processing of the current node
    + if current node is already visited and shifted weight is greater then shifted desired path weight in this node then
        + skip further processing of the current node
    + put current node to visited list and save path and weight
    + add all current node neighbors to visit queue with path and weight
+ current desired path is what we were looking for
+ if current desired path is empty then no path is found

### Weight function

Weight function is defined by path computation strategy.
For now four strategies are available:
* Cost

Manually assigned value for each link. If link is under maintenance and/or link is unstable then it's cost is increased by preconfigured value.

`Link weight = cost + underMaintenance * underMaintenanceCostRise + unstable * unstableCostRise` where underMaintenance and unstable are 0 when false and 1 when true.

* Cost and available bandwidth

The same as Cost strategy but if two paths has the same cost then the one with minimal sum of available bandwidths will be chosen.

* Latency

Automatically calculated value for each link. Separate maintenance/unstable penalties is used when calculating link weight based on latency in a similar way as for cost.

`Link weight = latency + underMaintenance * underMaintenanceLatencyRise + unstable * unstableLatencyRise` where underMaintenance and unstable are 0 when false and 1 when true. Exact value for `underMaintenanceLatencyRise` and `unstableLatencyRise` should be estimated with respect to average and maximal latency in the network. To avoid using unstable links in best path it's possible to choose 1 to 10 seconds latency penalties.

* Max latency

The same as Latency strategy but modified algorithm is used to find a path with latency as close as possible but less then max_latency param.

For more info see [PCE with weights computation strategies](../solutions/pce-weights-strategies/pce-weights-strategies.md).
