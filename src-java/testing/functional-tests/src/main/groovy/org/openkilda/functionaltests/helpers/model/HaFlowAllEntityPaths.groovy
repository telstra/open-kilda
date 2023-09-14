package org.openkilda.functionaltests.helpers.model

import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.testing.model.topology.TopologyDefinition

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder

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
}