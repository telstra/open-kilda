package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition

import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder

@Canonical
@EqualsAndHashCode(excludes = "topologyDefinition")
@Builder
@ToString(includeNames = true, excludes = 'topologyDefinition', includePackage = false)
class FlowEntityPath {
    FlowPathModel flowPath

    TopologyDefinition topologyDefinition

    FlowEntityPath(FlowPathPayload flowPathPayload, TopologyDefinition topologyDefinition) {
        this.flowPath = new FlowPathModel(
                flowId: flowPathPayload.id,
                path: new PathModel(
                        forward: new Path(flowPathPayload.forwardPath, topologyDefinition),
                        reverse: new Path(flowPathPayload.reversePath, topologyDefinition),
                        diverseGroup: !flowPathPayload?.diverseGroupPayload ? null :flowPathPayload.diverseGroupPayload
                ),
                protectedPath: !flowPathPayload?.protectedPath ? null : new PathModel(
                        forward: new Path(flowPathPayload.protectedPath.forwardPath, topologyDefinition),
                        reverse: new Path(flowPathPayload.protectedPath.reversePath, topologyDefinition),
                        diverseGroup: !flowPathPayload?.diverseGroupProtectedPayload ? null : flowPathPayload.diverseGroupProtectedPayload
                )
        )

        this.topologyDefinition = topologyDefinition
    }

    List<SwitchId> getInvolvedSwitches(Direction direction = Direction.FORWARD) {
        List<SwitchId> switches = []
        if (direction == Direction.FORWARD) {
            switches.addAll(flowPath.path.forward.getInvolvedSwitches() + flowPath?.protectedPath?.forward?.getInvolvedSwitches())
        } else {
            switches.addAll(flowPath.path.reverse.getInvolvedSwitches() + flowPath?.protectedPath?.reverse?.getInvolvedSwitches())
        }
        switches.findAll().unique()
    }

    List<PathNode> getPathNodes(Direction direction = Direction.FORWARD, boolean isProtected = false) {
        if (direction == Direction.FORWARD) {
            isProtected ? flowPath.protectedPath.forward.nodes.toPathNode() : flowPath.path.forward.nodes.toPathNode()
        } else {
            isProtected ? flowPath.protectedPath.reverse.nodes.toPathNode() : flowPath.path.reverse.nodes.toPathNode()
        }
    }
}

