package org.openkilda.functionaltests.helpers.factory

import org.openkilda.functionaltests.model.cleanup.CleanupManager

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.builder.HaFlowBuilder
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
class HaFlowFactory {
    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    CleanupManager cleanupManager
    @Autowired
    Database database

    /*
    This method allows customization of the HA-Flow with desired parameters for further creation
     */
    HaFlowBuilder getBuilder(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return new HaFlowBuilder(swT, northboundV2, topology, database, cleanupManager, useTraffgenPorts, busyEndpoints)
    }

    /*
    This method allows random HA-Flow creation on specified switches
    and waits for it to become UP.
     */

    HaFlowExtended getRandom(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return getBuilder(swT, useTraffgenPorts, busyEndpoints).build().create()
    }
}
