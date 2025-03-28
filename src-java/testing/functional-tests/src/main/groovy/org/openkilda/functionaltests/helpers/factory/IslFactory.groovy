package org.openkilda.functionaltests.helpers.factory

import org.openkilda.functionaltests.helpers.model.IslExtended
import org.openkilda.functionaltests.helpers.model.Switches
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class IslFactory {

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

    IslExtended get(Isl isl) {
        new IslExtended(isl, northbound, northboundV2, database, lockKeeper, topology, cleanupManager)
    }
}
