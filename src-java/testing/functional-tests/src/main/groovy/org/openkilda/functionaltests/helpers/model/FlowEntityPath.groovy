package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.model.stats.Direction.FORWARD

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

    List<SwitchId> getInvolvedSwitches(Direction direction = FORWARD) {
        List<SwitchId> switches = []
        if (direction == FORWARD) {
            switches.addAll(flowPath.path.forward.getInvolvedSwitches() + flowPath?.protectedPath?.forward?.getInvolvedSwitches())
        } else {
            switches.addAll(flowPath.path.reverse.getInvolvedSwitches() + flowPath?.protectedPath?.reverse?.getInvolvedSwitches())
        }
        switches.findAll().unique()
    }

    List<SwitchId> getMainPathSwitches(Direction direction = FORWARD){
        getPathInvolvedSwitches(flowPath.path, direction)
    }

    List<SwitchId> getProtectedPathSwitches(Direction direction = FORWARD){
        getPathInvolvedSwitches(flowPath.protectedPath, direction)
    }

    List<SwitchId> getMainPathTransitSwitches(Direction direction = FORWARD){
        getTransitSwitches(flowPath.path, direction)
    }

    List<SwitchId> getProtectedPathTransitSwitches(Direction direction = FORWARD){
        getTransitSwitches(flowPath.protectedPath, direction)
    }

    private List<SwitchId> getPathInvolvedSwitches(PathModel path, Direction direction) {
        direction == FORWARD ? path.forward.getInvolvedSwitches() : path.reverse.getInvolvedSwitches()
    }

    private List<SwitchId> getTransitSwitches(PathModel path, Direction direction = FORWARD) {
        direction == FORWARD ? path.forward.getTransitInvolvedSwitches() : path.reverse.getTransitInvolvedSwitches()
    }

    List<PathNode> getPathNodes(Direction direction = FORWARD, boolean isProtected = false) {
        PathModel path = isProtected ? flowPath.protectedPath : flowPath.path
        getPathInvolvedNodes(path, direction)
    }

    List<Isl> getMainPathInvolvedIsls(Direction direction = FORWARD) {
        getPathInvolvedIsls(flowPath.path, direction)
    }

    List<Isl> getProtectedPathInvolvedIsls(Direction direction = FORWARD) {
        getPathInvolvedIsls(flowPath.protectedPath, direction)
    }

    List<Isl> getInvolvedIsls(Direction direction = FORWARD) {
        flowPath.getInvolvedIsls(direction)
    }

    Path getMainPath(Direction direction = FORWARD) {
        direction == FORWARD ? flowPath.path.forward : flowPath.path.reverse
    }

    Path getProtectedPath(Direction direction = FORWARD) {
        direction == FORWARD ? flowPath.protectedPath.forward : flowPath.protectedPath.reverse
    }

    private List<Isl> getPathInvolvedIsls(PathModel path, Direction direction) {
        direction == FORWARD ? path.forward.getInvolvedIsls() : path.reverse.getInvolvedIsls()
    }

    private List<PathNode>  getPathInvolvedNodes(PathModel path, Direction direction) {
        direction == FORWARD ? path.forward.nodes.toPathNode() : path.reverse.nodes.toPathNode()
    }
}

