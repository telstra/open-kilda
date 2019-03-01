package org.openkilda.functionaltests.helpers

import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.messaging.model.FlowDto
import org.openkilda.messaging.model.FlowPairDto
import org.openkilda.messaging.payload.flow.FlowCreatePayload
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
     * Creates a FlowCreatePayload instance with random vlan and flow id. Will try to build over a traffgen port, or use
     * random port otherwise.
     */
    FlowCreatePayload randomFlow(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true,
                                 List<FlowCreatePayload> existingFlows = []) {
        Wrappers.retry(3, 0) {
            def newFlow = new FlowCreatePayload(generateFlowId(), getFlowEndpoint(srcSwitch, useTraffgenPorts),
                    getFlowEndpoint(dstSwitch, useTraffgenPorts), 500, false, false, "autotest flow", null, null, null,
                    null, null, null)
            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowCreatePayload
    }

    /**
     * Creates a FlowCreatePayload instance with random vlan and flow id suitable for a single-switch flow.
     * The flow will be on DIFFERENT PORTS. Will try to look for both ports to be traffgen ports.
     * But if such port is not available, will pick a random one. So in order to run a correct traffic
     * examination certain switch should have at least 2 traffgens connected to different ports.
     */
    FlowCreatePayload singleSwitchFlow(Switch sw, boolean useTraffgenPorts = true,
                                       List<FlowCreatePayload> existingFlows = []) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        Wrappers.retry(3, 0) {
            def srcEndpoint = getFlowEndpoint(sw, allowedPorts, useTraffgenPorts)
            def dstEndpoint = getFlowEndpoint(sw, allowedPorts - srcEndpoint.portNumber, useTraffgenPorts)
            def newFlow = new FlowCreatePayload(generateFlowId(), srcEndpoint, dstEndpoint, 500, false, false,
                    "autotest flow", null, null, null, null, null, null)
            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowCreatePayload
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
        return FlowPayload.builder()
                .id(generateFlowId())
                .source(srcEndpoint)
                .destination(dstEndpoint)
                .maximumBandwidth(500)
                .description("autotest flow")
                .build()
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
     * Updates flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely updated.
     */
    FlowPayload updateFlow(String flowId, FlowPayload flow) {
        def flowEntryBeforeUpdate = db.getFlow(flowId)

        log.debug("Updating flow '${flow.id}'")
        def response = northbound.updateFlow(flowId, flow)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        def flowEntryAfterUpdate = db.getFlow(flowId)

        // TODO(ylobankov): Delete check for rules installation once we add a new test to verify this functionality.
        checkRulesOnSwitches(flowEntryAfterUpdate, RULES_INSTALLATION_TIME, true)
        checkRulesOnSwitches(flowEntryBeforeUpdate, RULES_DELETION_TIME, false)

        return response
    }

    /**
     * Check whether given potential flow is conflicting with any of flows in the given list.
     * Usually used to ensure that some new flow is by accident is not conflicting with any of existing flows.
     * Verifies conflicts by flow id and by port-vlan conflict on source or destination switch.
     *
     * @param newFlow this flow will be validated against the passed list
     * @param existingFlows the passed flow will be validated against this list
     * @return true if passed flow conflicts with any of the flows in the list
     */
    static boolean flowConflicts(FlowPayload newFlow, List<FlowPayload> existingFlows) {
        List<FlowEndpointPayload> existingEndpoints = existingFlows.inject([]) { r, flow ->
            r << flow.source << flow.destination
        }
        [newFlow.source, newFlow.destination].any { newEp ->
            existingEndpoints.find {
                newEp.datapath == it.datapath && newEp.portNumber == it.portNumber &&
                        (newEp.vlanId == it.vlanId || it.vlanId == 0 || newEp.vlanId == 0)
            }
        } || existingFlows*.id.contains(newFlow.id)
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
    private String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
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
