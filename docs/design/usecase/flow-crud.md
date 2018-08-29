# USE-CASE: FLOW CRUD Discovery

## Introduction

This file introduces the design for flow management (create, read, update, deleted) within kilda.

The Flow CRUD use case focuses on the (mostly) user initiated management of flows. There are 
separate use cases for flow health checks, flow re-routing (ie network path failure, cost 
optimization, contract alignment wrt latency), and flow storm topology LCM (ie for anything
stateful, how does it regain the stat information after a re-launch).

## Development

Look in `base/hacks/usecase/flow.crud.make` for hints on how to develop and debug this UC.

## High Level Algorithm

When a user initiates a CRUD request, it starts with sending a request to the NB API. The NB API
will forward the request to the Flow Topology through a Kafka topic (kilda.flow). Because this is
an asynchronous operation (southbound from the API), a correlation ID is used to enable the NB
API to match the response.  

### Flow Creation

For Creation, the flow topology will get a path from the Path Computation Engine (PCE), which is a 
facade on top of Neo4j for release 1.0. With the path in hand, the flow engine will send a series 
of flow commands to the openflow speaker (floodlight) to install the flow rules on each switch in
 the path. After that is done successfully, the path is committed (ie distributed transaction) and
 a response is sent to the NB API.

#### Sequence Diagram
High Level Flow Creation:
![High level Flow Creation](./flow-crud-create.png "Flow Creation")

Detailed Flow Creation:
![Detailed Flow Creation](./flow-crud-create-full.png "Flow Creation (full)")

#### Diagram Text

This text can be used at https://www.websequencediagrams.com/

Diagram source file can be found [here](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-create-full.txt)
 
### Flow Deletion
![Flow Delete](./flow-crud-delete-full.png "Flow Delete (full)")

#### Diagram Text

This text can be used at https://www.websequencediagrams.com/

Diagram source file can be found [here](https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-delete-full.txt)

### Flow Update
TBD

### Flow Read
TBD

   
