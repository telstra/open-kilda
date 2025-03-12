package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.TopologyHelper.convertToPathNodePayload

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
    SwitchExtended src
    SwitchExtended dst
    List<List<PathNode>> paths
    @JsonIgnore
    NorthboundService northboundService
    @JsonIgnore
    TopologyDefinition topologyDefinition

    static SwitchPair singleSwitchInstance(SwitchExtended sw, NorthboundService northboundService = null) {
        new SwitchPair(src: sw, dst: sw, paths: [], northboundService: northboundService, topologyDefinition: null)
    }

    static SwitchPair withNonExistingDstSwitch(SwitchExtended src, NorthboundService northboundService) {
        def nonExistingSwitch = new Switch(null, NON_EXISTENT_SWITCH_ID,
                src.sw.getOfVersion(),
                src.sw.getStatus(),
                src.sw.getRegions(),
                src.sw.getOutPorts(),
                null, null, null)
        new SwitchPair(src: src, dst: new SwitchExtended(nonExistingSwitch, [], [], northboundService, null, null, null, null) ,
                paths: [], northboundService: northboundService, topologyDefinition: null)
    }

    List<Path> getPathsFromApi(Map<PathRequestParameter, Object> parameters = [:]) {
        List<PathDto> pathsFromApi = northboundService.getPaths([(SRC_SWITCH): src.switchId,
                                           (DST_SWITCH): dst.switchId] + parameters)
        return pathsFromApi.collect {new Path(it, topologyDefinition)}
    }

    @JsonIgnore
    List<SwitchExtended> toList() {
        return [src, dst]
    }

    @JsonIgnore
    List<Integer> getServicePorts() {
        src.getServicePorts() + dst.getServicePorts()
    }

    @JsonIgnore
    SwitchPair getReversed() {
        new SwitchPair(src: dst,
                dst: src,
                paths: paths.collect { it.reverse() },
                northboundService: northboundService,
                topologyDefinition: topologyDefinition)
    }

    List<List<PathNode>> possibleDefaultPaths() {
        paths.findAll { it.size() == paths.min { it.size() }.size() }
    }

    @Override
    String toString() {
        return "$src.switchId-$dst.switchId"
    }

    String hwSwString() {
        return "${src.hwSwString()}-${dst.hwSwString()}"
    }

    @JsonIgnore
    Boolean isTraffExamCapable() {
        return !(src.traffGenPorts.isEmpty() || dst.traffGenPorts.isEmpty())
    }

    static Closure NOT_WB_ENDPOINTS = {
        SwitchPair swP-> !swP.src.wb5164 && !swP.dst.wb5164
    }

    List<Path> retrieveAvailablePaths(){
       convertToPathNodePayload(paths).collect{
           new Path(it, topologyDefinition)
       }
    }
}
