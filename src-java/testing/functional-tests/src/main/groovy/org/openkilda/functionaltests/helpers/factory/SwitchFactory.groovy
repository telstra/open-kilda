package org.openkilda.functionaltests.helpers.factory

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
class SwitchFactory {

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    TopologyDefinition topology
    @Autowired
    Database database
    @Autowired
    LockKeeperService lockKeeper
    @Autowired
    CleanupManager cleanupManager

    SwitchExtended get(Switch sw) {
        List<Integer> islPorts = []
        topology.getRelatedIsls(sw).each {
            it?.srcSwitch?.dpId != sw.dpId ?: islPorts.add(it.srcPort)
            it?.dstSwitch?.dpId != sw.dpId ?: islPorts.add(it.dstPort)
        }
        List<Integer> traffGen = topology.traffGens.findAll{ it.switchConnected.dpId == sw.dpId }.switchPort
        return new SwitchExtended(sw, islPorts, traffGen,
                 northbound, northboundV2, database, lockKeeper, cleanupManager)
    }
}
