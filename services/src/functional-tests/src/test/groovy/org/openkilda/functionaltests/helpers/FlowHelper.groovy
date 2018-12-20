package org.openkilda.functionaltests.helpers

import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.messaging.model.FlowDto
import org.openkilda.messaging.model.FlowPairDto
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

import java.text.SimpleDateFormat

/**
 * Holds utility methods for manipulating flows.
 */
@Component
@Slf4j
class FlowHelper {
    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northbound
    @Autowired
    Database db

    def random = new Random()
    def faker = new Faker()
    def allowedVlans = 101..4095

    /**
     * Creates a FlowPayload instance with random vlan and flow id. Will try to build over a traffgen port, or use
     * random port otherwise.
     */
    FlowPayload randomFlow(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true) {
        return new FlowPayload(generateFlowName(), getFlowEndpoint(srcSwitch, useTraffgenPorts),
                getFlowEndpoint(dstSwitch, useTraffgenPorts), 500, false, false, "autotest flow", null, null)
    }

    /**
     * Creates a FlowPayload instance with random vlan and flow id suitable for a single-switch flow.
     * The flow will be on DIFFERENT PORTS. Will try to look for both ports to be traffgen ports.
     * But if such port is not available, will pick a random one. So in order to run a correct traffic
     * examination certain switch should have at least 2 traffgens connected to different ports.
     */
    FlowPayload singleSwitchFlow(Switch sw) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        def srcEndpoint = getFlowEndpoint(sw, allowedPorts)
        allowedPorts = allowedPorts - srcEndpoint.portNumber //do not pick the same port as in src
        def dstEndpoint = getFlowEndpoint(sw, allowedPorts)
        return new FlowPayload(generateFlowName(), srcEndpoint, dstEndpoint, 500,
                false, false, "autotest flow", null, null)
    }

    /**
     * Creates a FlowPayload instance with random vlan and flow id suitable for a single-switch flow.
     * The flow will be on the same port.
     */
    FlowPayload singleSwitchSinglePortFlow(Switch sw) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        def srcEndpoint = getFlowEndpoint(sw, allowedPorts)
        def dstEndpoint = getFlowEndpoint(sw, [srcEndpoint.portNumber])
        if (srcEndpoint.vlanId == dstEndpoint.vlanId) { //ensure same vlan is not randomly picked
            dstEndpoint.vlanId--
        }
        return new FlowPayload(generateFlowName(), srcEndpoint, dstEndpoint, 500,
                false, false, "autotest flow", null, null)
    }

    /**
     * Adds flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely created.
     */
    FlowPayload addFlow(FlowPayload flow) {
        log.debug("Adding flow '${flow.id}'")
        def response = northbound.addFlow(flow)

        def flowEntry = null
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP

            flowEntry = db.getFlow(flow.id)
            assert flowEntry
        }
        checkRulesOnSwitches(flowEntry, RULES_INSTALLATION_TIME, true)

        return response
    }

    /**
     * Deletes flow with checking rules on source and destination switches.
     * It is supposed if rules absent on source and destination switches, the flow is completely deleted.
     */
    FlowPayload deleteFlow(String flowId) {
        def flowEntry = db.getFlow(flowId)

        log.debug("Deleting flow '$flowId'")
        def response = northbound.deleteFlow(flowId)

        checkRulesOnSwitches(flowEntry, RULES_DELETION_TIME, false)

        return response
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
     * @param allowedPorts list of ports to randomly choose port from
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
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

    /**
     * Generates a unique name for all auto-tests flows.
     */
    private String generateFlowName() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "")
    }

    /**
     * Checks flow rules presence (or absence) on source and destination switches.
     */
    private void checkRulesOnSwitches(FlowPairDto<FlowDto, FlowDto> flowEntry, int timeout, boolean rulesPresent) {
        def cookies = [flowEntry.left.cookie, flowEntry.right.cookie]
        def switches = [flowEntry.left.sourceSwitch, flowEntry.left.destinationSwitch].toSet()
        switches.each { sw ->
            Wrappers.wait(timeout) {
                try {
                    def result = northbound.getSwitchRules(sw).flowEntries*.cookie
                    assert rulesPresent ? result.containsAll(cookies) : !result.any { it in cookies }
                } catch (HttpClientErrorException exc) {
                    if (exc.rawStatusCode == 404) {
                        log.warn("Switch '$sw' was not found when checking rules after flow "
                                + (rulesPresent ? "creation" : "deletion"))
                    } else {
                        throw exc
                    }
                }
            }
        }
    }
}
