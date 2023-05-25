package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.northbound.dto.v2.flows.FlowPathV2
import org.openkilda.testing.model.topology.TopologyDefinition
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PathFactory {
    @Autowired
    TopologyDefinition topologyDefinition

    Path get(List nodes) {
        if (nodes.isEmpty()) {
            return new Path([], topologyDefinition)
        }
        if (nodes.get(0) instanceof PathNode || nodes.get(0) instanceof FlowPathV2.PathNodeV2) {
            return new Path(nodes.collect { new FlowPathV2.PathNodeV2(it.getSwitchId(), it.getPortNo(), 0) }, topologyDefinition)
        }
        if (nodes.get(0) instanceof PathNodePayload) {
            def convertedNodes = nodes.collectMany {
                [new FlowPathV2.PathNodeV2(it.switchId, it.inputPort ?: 0, 0),
                 new FlowPathV2.PathNodeV2(it.switchId, it.outputPort ?: 0, 0)]
            }
            if (convertedNodes.size() > 2) {
                convertedNodes = convertedNodes[1..-2]
            }
            return new Path(convertedNodes, topologyDefinition)
        }
        throw new IllegalArgumentException("Can't create Path object from List of what you provided")
    }
}
