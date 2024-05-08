package org.openkilda.functionaltests.helpers.model

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.helpers.builder.YFlowBuilder
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
class YFlowFactory {

    @Autowired
    TopologyDefinition topology

    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2

    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    CleanupManager cleanupManager


    /*
    This method allows customization of the Y-Flow with desired parameters for further creation
     */

    YFlowBuilder getBuilder(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return new YFlowBuilder(swT, northbound, northboundV2, topology, cleanupManager, useTraffgenPorts, busyEndpoints)
    }

    /*
    This method allows random Y-Flow creation on specified switches
    and waits for it to become UP.
     */

    YFlowExtended getRandom(SwitchTriplet swT,
                            boolean useTraffgenPorts = true,
                            List<SwitchPortVlan> busyEndpoints = [],
                            CleanupAfter cleanupAfter = TEST) {
        return new YFlowBuilder(swT,
                northbound,
                northboundV2,
                topology,
                cleanupManager,
                useTraffgenPorts,
                busyEndpoints)
                .build()
        .create(FlowState.UP, cleanupAfter)
    }
}
