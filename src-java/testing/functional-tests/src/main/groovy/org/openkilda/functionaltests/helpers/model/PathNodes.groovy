package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.northbound.dto.v2.flows.FlowPathV2

import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false)
class PathNodes {
    List<FlowPathV2.PathNodeV2> nodes

    PathNodes(List<PathNodePayload> nodes) {
        this.nodes = nodes.collect {
            [it.inputPort ? new FlowPathV2.PathNodeV2(it.getSwitchId(), it.getInputPort(), null) : null,
             it.outputPort ? new FlowPathV2.PathNodeV2(it.getSwitchId(), it.getOutputPort(), null) : null]
        }
                .flatten()
                .findAll()
    }

    
}