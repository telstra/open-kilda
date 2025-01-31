package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.northbound.dto.v2.flows.FlowPathV2.PathNodeV2

import groovy.transform.Canonical
import groovy.transform.ToString

@Canonical
@ToString(includeNames = true, includePackage = false)
class PathNodes {
    List<PathNodeV2> nodes

    PathNodes(List<PathNodePayload> nodes) {
        this.nodes = nodes.collect {
            [it.inputPort ? new PathNodeV2(it.getSwitchId(), it.getInputPort(), null) : null,
             it.outputPort ? new PathNodeV2(it.getSwitchId(), it.getOutputPort(), null) : null]
        }
                .flatten()
                .findAll()
    }

    List<PathNode> toPathNode() {
        def seqId = 0
        List<PathNode> pathView = []
        if(nodes.size() > 2) {
            //remove first and last elements (not used in Path view)
            pathView = nodes.findAll().tail().collect { new PathNode(switchId: it.switchId, portNo: it.portNo)}
            pathView = pathView.dropRight(1)
        }
        pathView.each { it.seqId = seqId++ }

        pathView
    }

    List<PathNodeV2> toPathNodeV2() {
        List<PathNodeV2> pathView = []
        if(nodes.size() > 2) {
            //remove first and last elements (not used in Path view)
            pathView = nodes.findAll().tail()
            pathView = pathView.dropRight(1)
        }
        pathView
    }

    List<PathNodePayload> toPathNodePayload() {
        def result = [new PathNodePayload(nodes[0].getSwitchId(), null, nodes[0].getPortNo())]
        for (int i = 1; i < nodes.size() - 1; i += 2) {
            result.add(new PathNodePayload(nodes.get(i).getSwitchId(),
                    nodes.get(i).getPortNo(),
                    nodes.get(i + 1).getPortNo()))
        }
        result.add(new PathNodePayload(nodes[-1].getSwitchId(), nodes[-1].getPortNo(), null))
        return result
    }
}