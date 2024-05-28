package org.openkilda.functionaltests.helpers.factory


import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.builder.FlowBuilder
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope(SCOPE_PROTOTYPE)
class FlowFactory {

    @Autowired
    TopologyDefinition topology

    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    @Autowired
    CleanupManager cleanupManager

    @Autowired
    Database database

    /*
   This method allows customization of the Flow with desired parameters for further creation
    */
    FlowBuilder getBuilder(SwitchPair switchPair, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        getBuilder(switchPair.src, switchPair.dst, useTraffgenPorts, busyEndpoints)
    }

    FlowBuilder getBuilder(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return new FlowBuilder(srcSwitch, dstSwitch, northbound, northboundV2, topology, cleanupManager, database, useTraffgenPorts, busyEndpoints)
    }

    /*
    This method allows random Flow creation on specified switches and waits for it
    to become UP by default or to be in an expected state.
     */
    FlowExtended getRandom(SwitchPair switchPair, boolean useTraffgenPorts = true, FlowState expectedFlowState = UP,
                           List<SwitchPortVlan> busyEndpoints = []) {
        return getRandom(switchPair.src, switchPair.dst, useTraffgenPorts, expectedFlowState, busyEndpoints)
    }

    FlowExtended getRandom(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true, FlowState expectedFlowState = UP,
                           List<SwitchPortVlan> busyEndpoints = []) {
        return getBuilder(srcSwitch, dstSwitch, useTraffgenPorts, busyEndpoints).build().create(expectedFlowState)
    }

    FlowExtended getRandomV1(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true, FlowState expectedFlowState = UP,
                           List<SwitchPortVlan> busyEndpoints = []) {
        return getBuilder(srcSwitch, dstSwitch, useTraffgenPorts, busyEndpoints).build().createV1(expectedFlowState)
    }

    FlowExtended getRandomV1(SwitchPair switchPair, boolean useTraffgenPorts = true, FlowState expectedFlowState = UP,
                             List<SwitchPortVlan> busyEndpoints = []) {
        return getBuilder(switchPair.src, switchPair.dst, useTraffgenPorts, busyEndpoints).build().createV1(expectedFlowState)
    }
}
