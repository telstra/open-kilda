package org.openkilda.functionaltests.helpers.model

import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.model.LacpData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/* This class represents Link Aggregation Group */
class LAG {
    NorthboundServiceV2 northboundServiceV2
    TopologyDefinition.Switch aSwitch
    Integer physicalPort1
    Integer physicalPort2
    Integer logicalPort = null
    Boolean lacpReply

    LAG(NorthboundServiceV2 northboundServiceV2,
        TopologyDefinition.Switch aSwitch,
        Integer physicalPort1,
        Integer physicalPort2,
        Boolean lacpReply) {
        this.northboundServiceV2 = northboundServiceV2
        this.aSwitch = aSwitch
        this.physicalPort1 = physicalPort1
        this.physicalPort2 = physicalPort2
        this.lacpReply = lacpReply
    }

    void create() {
        def payload = new LagPortRequest([physicalPort1, physicalPort2] as Set<Integer>, lacpReply)
        def response = northboundServiceV2.createLagLogicalPort(aSwitch.getDpId(), payload)
        logicalPort = response.getLogicalPortNumber()
    }

    void delete() {
        northboundServiceV2.deleteLagLogicalPort(aSwitch.getDpId(), logicalPort)
    }

    LacpData getLacpData() {
        def data = northboundServiceV2.getLacpPortStatus(aSwitch.getDpId(), logicalPort).getData()

        return LacpData.builder()
        .expired(data.stateExpired)
        .defaulted(data.stateDefaulted)
        .distributing(data.stateDistributing)
        .collecting(data.stateCollecting)
        .synchronization(data.stateSynchronised)
        .aggregation(data.stateAggregatable)
        .lacpTimeout(data.stateShortTimeout)
        .lacpActivity(data.stateActive)
        .build()
    }
}


@Component
class LAGFactory {
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundServiceV2
    @Autowired
    TopologyDefinition toplogy

    LAG get(TopologyDefinition.Switch aSwitch,
    Integer physicalPort1,
    Integer physicalPort2,
    Boolean lacpReply=false) {
        return new LAG(northboundServiceV2, aSwitch, physicalPort1, physicalPort2, lacpReply)
    }

    LAG get(TopologyDefinition.Switch aSwitch) {
        def ports = this.toplogy.getAllowedPortsForSwitch(aSwitch).shuffled()[0, 1]
        return this.get(aSwitch, ports[0], ports[1])
    }
    
    LAG get(TopologyDefinition.Switch aSwitch, Integer mandatoryPhysicalPort, Boolean lacpReply) {
        def freePorts = toplogy.getAllowedPortsForSwitch(aSwitch)
        freePorts.removeAll([mandatoryPhysicalPort])
        def additionalPort = freePorts.shuffled().first()
        return this.get(aSwitch, mandatoryPhysicalPort, additionalPort, lacpReply)
    }
}

