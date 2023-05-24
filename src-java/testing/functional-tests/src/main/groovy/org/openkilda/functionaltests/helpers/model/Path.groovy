package org.openkilda.functionaltests.helpers.model

import groovy.transform.EqualsAndHashCode
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.northbound.dto.v2.flows.FlowPathV2
import org.openkilda.testing.model.topology.TopologyDefinition

/* This class represent any kind of flow path and is intended to help compare/manipulate paths
(presented as lists of nodes), received from different endpoints in the same manner.
To add the new way to represent the path, just add the one into PathFactory.get() method
Usage example:
def nodesFromRerouteResponse = haFlowHelper.reroute(haFlowId).getSharedPath().getNodes()
def nodesFromPathsResponse = haPathHelper.get(haFlowId).getSharedPath().getForward()
assert pathFactory.get(nodesFromRerouteResponse) == pathFactory.get(nodesFromPathsResponse)

This class has to replace *PathHelper in future
 */
@EqualsAndHashCode(excludes = "topologyDefinition")
class Path {
    List<FlowPathV2.PathNodeV2> nodes
    TopologyDefinition topologyDefinition

    Path(List<FlowPathV2.PathNodeV2> nodes, TopologyDefinition topologyDefinition) {
        if (nodes.size() % 2 != 0) {
            throw new IllegalArgumentException("Path should have even amount of nodes")
        }
        this.nodes = nodes
        this.topologyDefinition = topologyDefinition
    }

    List<Isl> getInvolvedIsls() {
        def isls = topologyDefinition.getIsls() + topologyDefinition.getIsls().collect { it.reversed }
        nodes.collate(2).collect { List<FlowPathV2.PathNodeV2> pathNodes ->
            isls.find {
                it.srcSwitch.dpId == pathNodes[0].switchId &&
                        it.srcPort == pathNodes[0].portNo &&
                        it.dstSwitch.dpId == pathNodes[1].switchId &&
                        it.dstPort == pathNodes[1].portNo
            }
        }
        /* TODO: add 'heavy' method to convert path to ISLs, which takes ISLs from Database, not from topology
        Such a method would be able to return ISLs which are not originated from topology, but were added in
        test runtime (e.g. by 're-plugging cable') */
    }
}
