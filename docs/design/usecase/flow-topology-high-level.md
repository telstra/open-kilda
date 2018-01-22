# Flow Topology (Storm) High Level design

## Introduction

This design is as of the 0.9 release.




## Sequence Diagram

### Sequence Diagram Graph

![Flow Topology](./flow-topology-high-level.png "Flow Topology (Storm)")


### Sequence Diagram Text

This text can be used at https://www.websequencediagrams.com/

```
title Flow Topology High Level Diagram

User->NB API: createFlow (src,dst,cost)
User->NB API: deleteFlow (flowId)
User->NB API: updateFlow (flowId,cost)
User->NB API: getFlow (flowId)
note over NB API: correlationId
NB API->FT-Splitter: kafka.flow
FT-Splitter->FT-CRUD: stateID
opt create or update
    FT-CRUD->FT-CRUD: handleCreate( FlowCreateRequest )
    FT-CRUD->PCE: getPath
    FT-CRUD->PCE: flowCache.createFlow()
    PCE->PCE: resourceCache.allocateFlow()
    FT-CRUD->FT-STREAM: CREATE, FlowInfoData
    FT-STREAM->CT-CacheBolt: handleFlowEvent
    CT-CacheBolt->TE: kilda.topo.eng 
    TE->TE: flow_operation
    TE->TE: create_flow (forward)
    loop install rules
        TE->TE: build_rules
        TE->Speaker: kafka.speaker
        TE->FT: kafka.flow
    end
    TE->TE: create_flow (reverse)
    loop install rules
        TE->TE: build_rules
        TE->Speaker: kafka.speaker
        TE->FT: kafka.flow
    end
    TE-->Neo4J: store_flow
    TE->Speaker: installRules
    TE->FT-CRUD: 
    TE-->?: flow_response
    FT->PCE: commit/rollback
    FT-CRUD->FT-STREAM: RESPONSE, InfoMessage(FlowResponse)
    FT-STREAM->FT-NORTHBOUND: 
    FT-NORTHBOUND->NB API: kilda.northbound
 end
opt delete
    FT->PCE: getPath
    loop delete rules
        FT->Speaker: kafka.speaker
    end
end
NB API->User: result
### 