package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

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

    List<SwitchId> getMainPathSwitches(Direction direction = Direction.FORWARD){
        getPathInvolvedSwitches(flowPath.path, direction)
    }

    List<SwitchId> getProtectedPathSwitches(Direction direction = Direction.FORWARD){
        getPathInvolvedSwitches(flowPath.protectedPath, direction)
    }

    List<SwitchId> getMainPathTransitSwitches(Direction direction = Direction.FORWARD){
        getTransitSwitches(flowPath.path, direction)
    }

    List<SwitchId> getProtectedPathTransitSwitches(Direction direction = Direction.FORWARD){
        getTransitSwitches(flowPath.protectedPath, direction)
    }

    private List<SwitchId> getPathInvolvedSwitches(PathModel path, Direction direction) {
        direction == Direction.FORWARD ? path.forward.getInvolvedSwitches() : path.reverse.getInvolvedSwitches()
    }

    private List<SwitchId> getTransitSwitches(PathModel path, Direction direction = Direction.FORWARD) {
        direction == Direction.FORWARD ? path.forward.getTransitInvolvedSwitches() : path.reverse.getTransitInvolvedSwitches()
    }

    List<PathNode> getPathNodes(Direction direction = Direction.FORWARD, boolean isProtected = false) {
        PathModel path = isProtected ? flowPath.protectedPath : flowPath.path
        getPathInvolvedNodes(path, direction)
    }

    List<Isl> getMainPathInvolvedIsls(Direction direction = Direction.FORWARD) {
        getPathInvolvedIsls(flowPath.path, direction)
    }

    List<Isl> getProtectedPathInvolvedIsls(Direction direction = Direction.FORWARD) {
        getPathInvolvedIsls(flowPath.protectedPath, direction)
    }

    List<Isl> getInvolvedIsls(Direction direction = Direction.FORWARD) {
        flowPath.getInvolvedIsls(direction)
    }

    private List<Isl> getPathInvolvedIsls(PathModel path, Direction direction) {
        direction == Direction.FORWARD ? path.forward.getInvolvedIsls() : path.reverse.getInvolvedIsls()
    }

    private List<PathNode>  getPathInvolvedNodes(PathModel path, Direction direction) {
        direction == Direction.FORWARD ? path.forward.nodes.toPathNode() : path.reverse.nodes.toPathNode()
    }
}

