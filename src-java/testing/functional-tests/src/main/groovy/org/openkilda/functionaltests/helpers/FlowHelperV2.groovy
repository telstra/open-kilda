package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS
import static org.openkilda.functionaltests.helpers.SwitchHelper.randomVlan
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_FLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.messaging.payload.flow.FlowState.IN_PROGRESS
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import java.text.SimpleDateFormat

/**
 * Holds utility methods for manipulating flows supporting version 2 of API.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class FlowHelperV2 {
    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    CleanupManager cleanupManager

    def random = new Random()
    def faker = new Faker()

    /**
     * Creates a FlowRequestV2 instance with random vlan and flow id.
     * Since multi-switch and single-switch flows have a bit different algorithms to create a correct flow, this method
     * will delegate the job to the correct algo based on src and dst switches passed.
     *
     * @param srcSwitch source endpoint
     * @param dstSwitch destination endpoint
     * @param useTraffgenPorts try using traffgen ports if available
     * @param existingFlows returned flow is guaranteed not to be in conflict with these flows
     */
    FlowRequestV2 randomFlow(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true,
                             List<FlowRequestV2> existingFlows = []) {
        if (srcSwitch.dpId == dstSwitch.dpId) {
            return singleSwitchFlow(srcSwitch, useTraffgenPorts, existingFlows)
        } else {
            return randomMultiSwitchFlow(srcSwitch, dstSwitch, useTraffgenPorts, existingFlows)
        }
    }

    FlowRequestV2 randomFlow(SwitchPair switchPair, boolean useTraffgenPorts = true,
                             List<FlowRequestV2> existingFlows = []) {
        randomFlow(switchPair.src, switchPair.dst, useTraffgenPorts, existingFlows)
    }

    FlowRequestV2 randomMultiSwitchFlow(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true,
                                        List<FlowRequestV2> existingFlows = []) {
        Wrappers.retry(3, 0) {
            def newFlow = FlowRequestV2.builder()
                    .flowId(generateFlowId())
                    .source(getFlowEndpoint(srcSwitch, useTraffgenPorts))
                    .destination(getFlowEndpoint(dstSwitch, useTraffgenPorts))
                    .maximumBandwidth(500)
                    .ignoreBandwidth(false)
                    .periodicPings(false)
                    .description(generateDescription())
                    .strictBandwidth(false)
                    .build()

            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowRequestV2
    }

    /**
     * Creates a FlowRequestV2 instance with random vlan and flow id suitable for a single-switch flow.
     * The flow will be on DIFFERENT PORTS. Will try to look for both ports to be traffgen ports.
     * But if such port is not available, will pick a random one. So in order to run a correct traffic
     * examination certain switch should have at least 2 traffgens connected to different ports.
     */
    FlowRequestV2 singleSwitchFlow(Switch sw, boolean useTraffgenPorts = true,
                                   List<FlowRequestV2> existingFlows = []) {
        Wrappers.retry(3, 0) {
            def srcEndpoint = getFlowEndpoint(sw, [], useTraffgenPorts)
            def dstEndpoint = getFlowEndpoint(sw, [srcEndpoint.portNumber], useTraffgenPorts)
            def newFlow = FlowRequestV2.builder()
                    .flowId(generateFlowId())
                    .source(srcEndpoint)
                    .destination(dstEndpoint)
                    .maximumBandwidth(500)
                    .ignoreBandwidth(false)
                    .periodicPings(false)
                    .description(generateDescription())
                    .strictBandwidth(false)
                    .build()
            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowRequestV2
    }

    /**
     * Adds flow and waits for it to become in expected state ('Up' by default)
     */
    FlowResponseV2 addFlow(FlowRequestV2 flow, FlowState expectedFlowState = UP, cleanupAfter = TEST) {
        log.debug("Adding flow '${flow.flowId}'")
        def flowId = flow.getFlowId()
        cleanupManager.addAction(DELETE_FLOW, {safeDeleteFlow(flowId)}, cleanupAfter)
        def response = northboundV2.addFlow(flow)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert northboundV2.getFlowStatus(flowId).status == expectedFlowState
            if (expectedFlowState != IN_PROGRESS) {
                assert northbound.getFlowHistory(flowId).any {it.payload.last().action == CREATE_SUCCESS}
            }
        }
        return response
    }


    /**
     * Sends delete request for flow and waits for that flow to disappear from flows list
     */
    FlowResponseV2 deleteFlow(String flowId) {
        Wrappers.wait(WAIT_OFFSET * 2) { assert northboundV2.getFlowStatus(flowId).status != FlowState.IN_PROGRESS }
        log.debug("Deleting flow '$flowId'")
        def response = northboundV2.deleteFlow(flowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !northboundV2.getFlowStatus(flowId)
            assert northbound.getFlowHistory(flowId).find { it.payload.last().action == DELETE_SUCCESS }
        }
        return response
    }

    def safeDeleteFlow(String flowId) {
        if (flowId in northboundV2.getAllFlows()*.getFlowId()) {
            deleteFlow(flowId)
        }
    }

    String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
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
    static boolean flowConflicts(FlowRequestV2 newFlow, List<FlowRequestV2> existingFlows) {
        List<FlowEndpointV2> existingEndpoints = existingFlows.collectMany { [it.source, it.destination] }
        [newFlow.source, newFlow.destination].any { newEp ->
            existingEndpoints.find {
                newEp.switchId == it.switchId && newEp.portNumber == it.portNumber &&
                        (newEp.vlanId == it.vlanId || it.vlanId == 0 || newEp.vlanId == 0)
            }
        } || existingFlows*.flowId.contains(newFlow.flowId)
    }




    static FlowRequestV2 toV2(FlowPayload flow) {
        FlowRequestV2.builder()
                .flowId(flow.id)
                .description(flow.description)
                .maximumBandwidth(flow.maximumBandwidth)
                .ignoreBandwidth(flow.ignoreBandwidth)
                .allocateProtectedPath(flow.allocateProtectedPath)
                .periodicPings(flow.periodicPings)
                .encapsulationType(flow.encapsulationType)
                .maxLatency(flow.maxLatency)
                .pinned(flow.pinned)
                .priority(flow.priority)
                .source(toV2(flow.source))
                .destination(toV2(flow.destination))
                .build()
    }

    static FlowEndpointV2 toV2(FlowEndpointPayload ep) {
        FlowEndpointV2.builder()
                .switchId(ep.getSwitchDpId())
                .portNumber(ep.getPortId())
                .vlanId(ep.getVlanId())
                .detectConnectedDevices(toV2(ep.detectConnectedDevices))
                .build()
    }

    static DetectConnectedDevicesV2 toV2(DetectConnectedDevicesPayload payload) {
        new DetectConnectedDevicesV2(payload.lldp, payload.arp)
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param useTraffgenPorts whether to try finding a traffgen port
     */
    FlowEndpointV2 getFlowEndpoint(Switch sw, boolean useTraffgenPorts = true) {
        getFlowEndpoint(sw, [], useTraffgenPorts)
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param excludePorts list of ports that should not be picked
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen
     */
    FlowEndpointV2 getFlowEndpoint(Switch sw, List<Integer> excludePorts,
            boolean useTraffgenPorts = true) {
        def ports = topology.getAllowedPortsForSwitch(sw) - excludePorts
        int port = ports[random.nextInt(ports.size())]
        if (useTraffgenPorts) {
            List<Integer> tgPorts = sw.traffGens*.switchPort - excludePorts
            if (tgPorts) {
                port = tgPorts[0]
            }
        }
        return new FlowEndpointV2(
                sw.dpId, port, randomVlan(),
                new DetectConnectedDevicesV2(false, false))
    }

    private String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }
}
