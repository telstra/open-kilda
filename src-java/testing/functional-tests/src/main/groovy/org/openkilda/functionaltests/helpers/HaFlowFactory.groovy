package org.openkilda.functionaltests.helpers


import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.builder.HaFlowBuilder
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.testing.model.topology.TopologyDefinition
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

    /*
    This method allows customization of the HA-Flow with desired parameters for further creation
     */
    HaFlowBuilder getBuilder(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return new HaFlowBuilder(swT, northboundV2, topology, useTraffgenPorts, busyEndpoints)
    }

    /*
    This method allows random HA-Flow creation on specified switches
    and waits for it to become UP.
     */

    HaFlowExtended getRandom(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        HaFlowBuilder haFlowBuilder = getBuilder(swT, useTraffgenPorts, busyEndpoints)
        def response = haFlowBuilder.build()
        assert response.haFlowId
        HaFlow haFlow = null
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            haFlow = northboundV2.getHaFlow(response.haFlowId)
            assert haFlow.status == FlowState.UP.toString()
                    && haFlow.getSubFlows().status.unique() == [FlowState.UP.toString()], "Flow: ${haFlow}"
        }
        return new HaFlowExtended(haFlow, northboundV2, topology)
    }
}
