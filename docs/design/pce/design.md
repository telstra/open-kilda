# Path Computation Engine (PCE)

## Overview

PCE is a open-kilda module responsible for any network path computations. The main goal is to provide simple API to calculate all paths required for any system component.

## API

All components should interact with PCE through PathComputer interface.
PathComputer provides following options:
* Calculate best path for flow

Criteria for best path computation is dependent on flow **path computation strategy** param. For now two options are available: cost and latency. Only links with appropriate **flow encapsulation type** and sufficient bandwidth can be used as available network resources. **Ignore bandwidth flag** may be used to turn off bandwidth limitation. Additionally **flow group id** param can be used to create [diverse flows](../solutions/pce-diverse-flows/pce-diverse-flows.md).

* Calculate path for flow with ability to reuse provided network resources

The same as previous but available network resources are extended with the list of paths which resources may be reused. 

* Calculate list of best available paths

Criteria for best path computation is dependent on **path computation strategy** param. **Encapsulation type** param is used to calculate available network resources in the same way. Best paths are sorted in descending order of MinAvailableBandwidth.

## Algorithm

Breadth-first search with path weight calculation and path length limitation is used to calculate best path. 

Starting from source node we visit all neighbor nodes and saving path to all found nodes with lowest total weight. 

Algorithm pseudocode:
1. add source node to visit queue
2. for each node in visit queue
    1. if node is destination
        1. if path weight is lower then current best 
            1. save new path as current best path
        2. skip further processing of the current node
    2. if current node is already visited and it's weight is greater then previously found one 
        1. skip further processing of the current node
    3. if path to current node is longer then depth limit or it's weight is greater then current best path weight
        1. skip further processing of the current node
    4. put current node to visited list and save path and weight
    5. add all current node neighbors to visit queue with path and weight
3. current best path is the best one
4. if current best path is empty then no path is found

### Weight function

Weight function is defined by path computation strategy.
For now two strategies are available:
* Cost

Manually assigned value for each link. If link is under maintenance and/or link is unstable then it's cost is increased by preconfigured value.

* Latency

Automatically calculated value for each link. Separate maintenance/unstable penalties is used when calculating link weight based on latency in a similar way as for cost.

For more info see [PCE with weights computation strategies](../solutions/pce-weights-strategies/pce-weights-strategies.md).
