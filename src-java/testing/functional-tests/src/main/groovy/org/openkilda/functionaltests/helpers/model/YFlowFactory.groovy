package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS_Y
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.YFlowBuilder
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.testing.model.topology.TopologyDefinition
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
    and waits for it to become UP.
     */
    YFlowExtended getRandom(SwitchTriplet swT, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        YFlowBuilder yFlowBuilder = getBuilder(swT, useTraffgenPorts, busyEndpoints)
        def response = yFlowBuilder.build()
        assert response.yFlowId
        YFlow yFlow = null
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            yFlow = northboundV2.getYFlow(response.yFlowId)
            assert yFlow
            assert yFlow.status == FlowState.UP.toString()
            assert northbound.getFlowHistory(response.yFlowId).last().payload.last().action == CREATE_SUCCESS_Y
        }
        new YFlowExtended(yFlow, northbound, northboundV2, topology)

    }
}
