package org.openkilda.functionaltests.helpers.model

import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
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
class HaFlowAllEntityPaths {

    FlowPathModel sharedPath
    List<FlowPathModel> subFlowPaths

    TopologyDefinition topologyDefinition

    HaFlowAllEntityPaths(HaFlowPaths haFlowPaths, TopologyDefinition topologyDefinition) {
        this.sharedPath = new FlowPathModel(
                path: new PathModel(
                        forward: new Path(haFlowPaths.sharedPath.forward, topologyDefinition),
                        reverse: new Path(haFlowPaths.sharedPath.reverse, topologyDefinition)
                ),
                protectedPath: !sharedPath?.protectedPath ? null : new PathModel(
                        forward: new Path(haFlowPaths.sharedPath.protectedPath.forward, topologyDefinition),
                        reverse: new Path(haFlowPaths.sharedPath.protectedPath.reverse, topologyDefinition)
                ))

        this.subFlowPaths = haFlowPaths.subFlowPaths.collect { subFlow ->
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

    List<Isl> getInvolvedIsls(boolean isForward = true) {
        subFlowPaths.collect { it.getInvolvedIsls(isForward)}.flatten().unique() as List<Isl>
    }


    List<SwitchId> getInvolvedSwitches(boolean isForward = true) {
        List<SwitchId> switches = []
        if(isForward) {
            switches.addAll(sharedPath.path.forward.nodes.nodes.switchId + sharedPath?.protectedPath?.forward?.nodes?.nodes?.switchId)
            subFlowPaths.each {subFlowPath ->
                switches.addAll(subFlowPath.path.forward.nodes.nodes.switchId + subFlowPath?.protectedPath?.forward?.nodes?.nodes?.switchId)
            }
        } else {
            switches.addAll(sharedPath.path.reverse.nodes.nodes.switchId + sharedPath?.protectedPath?.reverse?.nodes?.nodes?.switchId)
            subFlowPaths.each {subFlowPath ->
                switches.addAll(subFlowPath.path.reverse.nodes.nodes.switchId + subFlowPath?.protectedPath?.reverse?.nodes?.nodes?.switchId)
            }
        }
        switches.findAll().unique()
    }
}
