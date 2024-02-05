package org.openkilda.functionaltests.helpers.model


import org.openkilda.functionaltests.helpers.builder.YFlowBuilder
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Slf4j
@Component
class YFlowFactory {

    @Autowired
    TopologyDefinition topology

    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound


    /*
    This method allows customization of the Y-Flow with desired parameters for further creation
     */
    YFlowBuilder getBuilder(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return new YFlowBuilder(swT, northbound, northboundV2, topology, useTraffgenPorts, busyEndpoints)
    }

    /*
    This method allows random Y-Flow creation on specified switches
     */
    YFlowExtended getRandom(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        YFlowBuilder yFlowBuilder = getBuilder(swT, useTraffgenPorts, busyEndpoints)
        yFlowBuilder.build()

    }
}
