package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class FlowHelper {
    @Autowired
    TopologyDefinition topology

    def timestamp = new Date().format("ddMMMHHmmss")
    def random = new Random()
    def allowedVlans = 101..4095

    FlowPayload randomFlow(Switch srcSwitch, Switch dstSwitch) {
        new FlowPayload(timestamp, getFlowEndpoint(srcSwitch), getFlowEndpoint(dstSwitch), 500,
                false, "autotest flow", null, null)
    }

    /**
     * Returns flow endpoint with randomly chosen port and vlan.
     */
    private getFlowEndpoint(Switch sw) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        return new FlowEndpointPayload(sw.dpId, allowedPorts[random.nextInt(allowedPorts.size())],
                allowedVlans[random.nextInt(allowedVlans.size())])
    }
}
