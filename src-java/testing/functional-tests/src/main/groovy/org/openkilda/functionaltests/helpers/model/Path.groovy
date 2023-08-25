package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.payload.flow.PathNodePayload

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.northbound.dto.v2.flows.FlowPathV2
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.payloads.PathDto
import org.openkilda.testing.service.northbound.payloads.ProtectedPathPayload

/* This class represent any kind of flow path and is intended to help compare/manipulate paths
(presented as lists of nodes), received from different endpoints in the same manner.
This class has to replace *PathHelper in future
 */
@EqualsAndHashCode(excludes = "topologyDefinition")
@Builder
@ToString(includeNames = true, excludes = 'topologyDefinition', includePackage = false)
class Path {
    PathNodes nodes
    TopologyDefinition topologyDefinition
    Long bandwidth;
    Long latency;
    Long latencyNs;
    Long latencyMs;
    Boolean isBackupPath;
    Path protectedPath;

    Path(PathDto pathDto, TopologyDefinition topologyDefinition) {
        this.nodes = new PathNodes(pathDto.nodes)
        this.topologyDefinition = topologyDefinition
        this.bandwidth = pathDto.bandwidth
        this.latency = pathDto.latency
        this.latencyNs = pathDto.latencyNs
        this.latencyMs = pathDto.latencyMs
        this.isBackupPath = pathDto.isBackupPath
        def protectedPath = pathDto.getProtectedPath()
        this.protectedPath = protectedPath ? new Path(pathDto.getProtectedPath(), topologyDefinition) : null
    }

    Path(ProtectedPathPayload pathDto, TopologyDefinition topologyDefinition) {
        this.nodes = new PathNodes(pathDto.nodes)
        this.topologyDefinition = topologyDefinition
        this.bandwidth = pathDto.bandwidth
        this.latency = pathDto.latency
        this.latencyNs = pathDto.latencyNs
        this.latencyMs = pathDto.latencyMs
        this.isBackupPath = pathDto.isBackupPath
    }

    Path(List<PathNodePayload> nodes, TopologyDefinition topologyDefinition) {
        this.nodes = new PathNodes(nodes)
        this.topologyDefinition = topologyDefinition
    }

    List<Isl> getInvolvedIsls() {
        def isls = topologyDefinition.getIsls() + topologyDefinition.getIsls().collect { it.reversed }
        nodes.getNodes().collate(2, 1, false).collect { List<FlowPathV2.PathNodeV2> pathNodes ->
            isls.find {
                it.srcSwitch.dpId == pathNodes[0].switchId &&
                        it.srcPort == pathNodes[0].portNo &&
                        it.dstSwitch.dpId == pathNodes[1].switchId &&
                        it.dstPort == pathNodes[1].portNo
            }
        }.findAll()
        /* TODO: add 'heavy' method to convert path to ISLs, which takes ISLs from Database, not from topology
        Such a method would be able to return ISLs which are not originated from topology, but were added in
        test runtime (e.g. by 're-plugging cable') */
    }

    boolean canBeProtectedFor(Path otherPath) {
        return otherPath && getInvolvedIsls().intersect(otherPath.getInvolvedIsls()).isEmpty()
    }

    boolean hasProtectedPathWithLatencyAbove(Long latencyMs) {
        return protectedPath && protectedPath.getLatencyMs() > latencyMs
    }
}
