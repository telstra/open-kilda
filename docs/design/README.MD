
# Introduction
This document describes OpenKilda design including main use cases, components design, and their interactions.

Overall components [interaction](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/northbound-and-flow-topology.svg) among Northbound, Kafka, Flow topology, Cache topology and Topology engine.

[Kilda Domain Model](domain-model/)

## Flow operations
1. [Flow Management design document](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud.md).
The document describes main use cases for the flow operations create, delete, read, update and ping:
- [Create Flow](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-create-full.png) sequence diagram 
- [Create Flow](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-create-full.txt) sequence source file
- [Delete Flow](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-delete-full.png) sequence diagram
- [Delete Flow](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-delete-full.txt) sequence source file
2. [Flow Validation](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-ping-manual.md) mechanism description designed for sending pings along a flow path in order to verify its latency and switch availability along the flow path

## Switch operations
1. [Delete meter](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/meter-delete.md) document describes the implementation of deleting a meter from the switch.

## Network discovery
[The document](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/discovery/network-discovery.md) contains 
network discovery design implemented in OpenKilda.

## Flow Topology
[The document](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-topology-high-level.md) 
contains high-level flow topology design implemented in Storm.
There is also an overall [description](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/storm-topology-management.md)
of the patterns as to how topologies work in OpenKilda in Storm.

## Kafka
[The document](https://github.com/telstra/open-kilda/blob/develop/docs/design/kafka.md)
contains Kafka design implementation.

## Path Computation engine (PCE)
[The document](https://github.com/telstra/open-kilda/blob/develop/docs/design/path-computation.md) contains high level
PCE description.

## Floodlight
[The document](https://github.com/telstra/open-kilda/blob/develop/docs/design/solutions/floodlight/floodlight-guaranteed-processing.md) floodlight design and implementation
