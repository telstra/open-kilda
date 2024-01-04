package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.payloads.PathDto
import org.openkilda.testing.service.northbound.payloads.PathRequestParameter

import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.DST_SWITCH
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.SRC_SWITCH

@TupleConstructor
@EqualsAndHashCode
class SwitchPair {
    Switch src
    Switch dst
    List<List<PathNode>> paths
    @JsonIgnore
    NorthboundService northboundService
    @JsonIgnore
    TopologyDefinition topologyDefinition

    static SwitchPair singleSwitchInstance(Switch sw, NorthboundService northboundService = null) {
        new SwitchPair(src: sw, dst: sw, paths: [], northboundService: northboundService, topologyDefinition: null)
    }

    static SwitchPair withNonExistingDstSwitch(Switch src, NorthboundService northboundService) {
        def nonExistingSwitch = new Switch(null, NON_EXISTENT_SWITCH_ID,
                src.getOfVersion(),
                src.getStatus(),
                src.getRegions(),
                src.getOutPorts(),
                null, null, null)
        new SwitchPair(src: src, dst:nonExistingSwitch, paths: [], northboundService: northboundService, topologyDefinition: null)
    }

    List<Path> getPathsFromApi(Map<PathRequestParameter, Object> parameters = [:]) {
        List<PathDto> pathsFromApi = northboundService.getPaths([(SRC_SWITCH): src.getDpId(),
                                           (DST_SWITCH): dst.getDpId()] + parameters)
        return pathsFromApi.collect {new Path(it, topologyDefinition)}
    }

    @JsonIgnore
    SwitchPair getReversed() {
        new SwitchPair(src: dst,
                dst: src,
                paths: paths.collect { it.reverse() },
                northboundService: northboundService,
                topologyDefinition: topologyDefinition)
    }

    boolean hasOf13Path() {
        def possibleDefaultPaths = paths.findAll {
            it.size() == paths.min { it.size() }.size()
        }
        !possibleDefaultPaths.find { path ->
            path[1..-2].every { it.switchId.description.contains("OF_12") }
        }
    }

    @Override
    String toString() {
        return "$src.dpId-$dst.dpId"
    }

    String hwSwString() {
        return "$src.hwSwString-$dst.hwSwString"
    }

    @JsonIgnore
    Boolean isTraffExamCapable() {
        return !(src.getTraffGens().isEmpty() || dst.getTraffGens().isEmpty())
    }
}
