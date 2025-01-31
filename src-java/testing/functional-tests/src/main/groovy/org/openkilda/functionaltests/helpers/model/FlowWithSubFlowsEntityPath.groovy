package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths
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
class FlowWithSubFlowsEntityPath {

    FlowPathModel sharedPath
    List<FlowPathModel> subFlowPaths

    TopologyDefinition topologyDefinition

    FlowWithSubFlowsEntityPath(HaFlowPaths flowWithSubFlowsPaths, TopologyDefinition topologyDefinition) {
        this.sharedPath = new FlowPathModel(
                path: new PathModel(
                        forward: new Path(flowWithSubFlowsPaths.sharedPath.forward, topologyDefinition),
                        reverse: new Path(flowWithSubFlowsPaths.sharedPath.reverse, topologyDefinition)
                ),
                protectedPath: !sharedPath?.protectedPath ? null : new PathModel(
                        forward: new Path(flowWithSubFlowsPaths.sharedPath.protectedPath.forward, topologyDefinition),
                        reverse: new Path(flowWithSubFlowsPaths.sharedPath.protectedPath.reverse, topologyDefinition)
                ))

        this.subFlowPaths = flowWithSubFlowsPaths.subFlowPaths.collect { subFlow ->
            new FlowPathModel(
                    flowId: subFlow.flowId,
                    path: new PathModel(
                            forward: new Path(subFlow.forward, topologyDefinition),
                            reverse: new Path(subFlow.reverse, topologyDefinition)
                    ),
                    protectedPath: !subFlow?.protectedPath ? null : new PathModel(
                            forward: new Path(subFlow.protectedPath.forward, topologyDefinition),
                            reverse: new Path(subFlow.protectedPath.reverse, topologyDefinition)
                    )
            )
        }

        this.topologyDefinition = topologyDefinition
    }

    FlowWithSubFlowsEntityPath(YFlowPaths flowWithSubFlowsPaths, TopologyDefinition topologyDefinition) {
        this.sharedPath = new FlowPathModel(
                path: new PathModel(
                        forward: new Path(flowWithSubFlowsPaths.sharedPath.forward, topologyDefinition),
                        reverse: new Path(flowWithSubFlowsPaths.sharedPath.reverse, topologyDefinition),
                        diverseGroup: flowWithSubFlowsPaths.sharedPath.diverseGroup
                ),
                protectedPath: !sharedPath?.protectedPath ? null : new PathModel(
                        forward: new Path(flowWithSubFlowsPaths.sharedPath.protectedPath.forward, topologyDefinition),
                        reverse: new Path(flowWithSubFlowsPaths.sharedPath.protectedPath.reverse, topologyDefinition),
                        diverseGroup: flowWithSubFlowsPaths.sharedPath.protectedPath.diverseGroup
                ))

        this.subFlowPaths = flowWithSubFlowsPaths.subFlowPaths.collect { subFlow ->
            new FlowPathModel(
                    flowId: subFlow.flowId,
                    path: new PathModel(
                            forward: new Path(subFlow.forward, topologyDefinition),
                            reverse: new Path(subFlow.reverse, topologyDefinition),
                            diverseGroup: subFlow.diverseGroup

                    ),
                    protectedPath: !subFlow?.protectedPath ? null : new PathModel(
                            forward: new Path(subFlow.protectedPath.forward, topologyDefinition),
                            reverse: new Path(subFlow.protectedPath.reverse, topologyDefinition),
                            diverseGroup: subFlow.diverseGroup
                    )
            )
        }

        this.topologyDefinition = topologyDefinition
    }

    List<Isl> getInvolvedIsls(Direction direction = Direction.FORWARD) {
        subFlowPaths.collect { it.getInvolvedIsls(direction) }.flatten().unique() as List<Isl>
    }

    List<Isl> getSubFlowIsls(String subFlowId, Direction direction = Direction.FORWARD) {
        subFlowPaths.find { it.flowId == subFlowId}.getInvolvedIsls(direction)
    }

    List<SwitchId> getInvolvedSwitches(Direction direction = Direction.FORWARD) {
        List<SwitchId> switches = []
        if (direction == Direction.FORWARD) {
            switches.addAll(sharedPath.path.forward.getInvolvedSwitches() + sharedPath?.protectedPath?.forward?.getInvolvedSwitches())
            subFlowPaths.each { subFlowPath ->
                switches.addAll(subFlowPath.path.forward.getInvolvedSwitches() + subFlowPath?.protectedPath?.forward?.getInvolvedSwitches())
            }
        } else {
            switches.addAll(sharedPath.path.reverse.getInvolvedSwitches() + sharedPath?.protectedPath?.reverse?.getInvolvedSwitches())
            subFlowPaths.each { subFlowPath ->
                switches.addAll(subFlowPath.path.reverse.getInvolvedSwitches() + subFlowPath?.protectedPath?.reverse?.getInvolvedSwitches())
            }
        }
        switches.findAll().unique()
    }
}
