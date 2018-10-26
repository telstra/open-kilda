package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.text.SimpleDateFormat

@Component
class FlowHelper {
    @Autowired
    TopologyDefinition topology

    def random = new Random()
    def allowedVlans = 101..4095
    def sdf = new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US)

    /**
     * Creates a FlowPayload instance with random vlan and flow id. Will try to build over a traffgen port, or use
     * random port otherwise.
     */
    FlowPayload randomFlow(Switch srcSwitch, Switch dstSwitch) {
        return new FlowPayload(sdf.format(new Date()), getFlowEndpoint(srcSwitch), getFlowEndpoint(dstSwitch), 500,
                false, false, "autotest flow", null, null)
    }

    /**
     * Single-switch flow with random vlan. The flow will be on DIFFERENT PORTS. Will try to look for both
     * ports to be traffgen ports. But if such port is not available, will pick a random one. So in order to run a
     * correct traffic examination certain switch should have at least 2 traffgens connected to different ports.
     */
    FlowPayload singleSwitchFlow(Switch sw) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        def srcEndpoint = getFlowEndpoint(sw, allowedPorts)
        allowedPorts = allowedPorts - srcEndpoint.portNumber //do not pick the same port as in src
        def dstEndpoint = getFlowEndpoint(sw, allowedPorts)
        return new FlowPayload(sdf.format(new Date()), srcEndpoint, dstEndpoint, 500,
                false, false, "autotest flow", null, null)
    }

    /**
     * Single-switch flow with random vlan. The flow will be on the same port.
     */
    FlowPayload singleSwitchSinglePortFlow(Switch sw) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        def srcEndpoint = getFlowEndpoint(sw, allowedPorts)
        def dstEndpoint = getFlowEndpoint(sw, [srcEndpoint.portNumber])
        if(srcEndpoint.vlanId == dstEndpoint.vlanId) { //ensure same vlan is not randomly picked
            dstEndpoint.vlanId--
        }
        return new FlowPayload(sdf.format(new Date()), srcEndpoint, dstEndpoint, 500,
                false, false, "autotest flow", null, null)
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param useTraffgenPorts whether to try finding a traffgen port
     */
    private FlowEndpointPayload getFlowEndpoint(Switch sw, boolean useTraffgenPorts = true) {
        getFlowEndpoint(sw, topology.getAllowedPortsForSwitch(sw), useTraffgenPorts)
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param allowedPorts list of ports to randomly choose from.
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present in
     * 'allowedPorts
     * @return
     */
    private FlowEndpointPayload getFlowEndpoint(Switch sw, List<Integer> allowedPorts,
            boolean useTraffgenPorts = true) {
        def port = allowedPorts[random.nextInt(allowedPorts.size())]
        if (useTraffgenPorts) {
            def connectedTraffgens = topology.activeTraffGens.findAll { it.switchConnected == sw }
            if (!connectedTraffgens.empty) {
                port = connectedTraffgens.find { allowedPorts.contains(it.switchPort) }?.switchPort ?: port
            }
        }
        return new FlowEndpointPayload(sw.dpId, port, allowedVlans[random.nextInt(allowedVlans.size())])
    }
}
