package org.openkilda.functionaltests.helpers

import static FlowHistoryConstants.UPDATE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_MIRROR_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowPathStatus
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
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

    def random = new Random()
    def faker = new Faker()
    def allowedVlans = 101..4095

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
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        Wrappers.retry(3, 0) {
            def srcEndpoint = getFlowEndpoint(sw, allowedPorts, useTraffgenPorts)
            def dstEndpoint = getFlowEndpoint(sw, allowedPorts - srcEndpoint.portNumber, useTraffgenPorts)
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
     * Creates a FlowPayload instance with random vlan and flow id suitable for a single-switch flow.
     * The flow will be on the same port.
     */
    FlowRequestV2 singleSwitchSinglePortFlow(Switch sw) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        def srcEndpoint = getFlowEndpoint(sw, allowedPorts)
        def dstEndpoint = getFlowEndpoint(sw, [srcEndpoint.portNumber])
        if (srcEndpoint.vlanId == dstEndpoint.vlanId) { //ensure same vlan is not randomly picked
            dstEndpoint.vlanId--
        }
        return FlowRequestV2.builder()
                            .flowId(generateFlowId())
                            .source(srcEndpoint)
                            .destination(dstEndpoint)
                            .maximumBandwidth(500)
                            .description(generateDescription())
                            .build()
    }

    /**
     * Adds flow and waits for it to become UP
     */
    FlowResponseV2 addFlow(FlowRequestV2 flow) {
        log.debug("Adding flow '${flow.flowId}'")
        def response = northboundV2.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
            assert northbound.getFlowHistory(flow.flowId).last().payload.last().action == CREATE_SUCCESS
        }
        return response
    }

    /**
     * Adds flow and waits for it to become UP
     */
    FlowResponseV2 addFlow(FlowCreatePayload flow) {
        return addFlow(toV2(flow));
    }

    /**
     * Sends delete request for flow and waits for that flow to disappear from flows list
     */
    FlowResponseV2 deleteFlow(String flowId) {
        Wrappers.wait(WAIT_OFFSET * 2) { assert northboundV2.getFlowStatus(flowId).status != FlowState.IN_PROGRESS }
        log.debug("Deleting flow '$flowId'")
        def response = northboundV2.deleteFlow(flowId)
        Wrappers.wait(WAIT_OFFSET) {
            assert !northboundV2.getFlowStatus(flowId)
            assert northbound.getFlowHistory(flowId).find { it.payload.last().action == DELETE_SUCCESS }
        }
        return response
    }

    /**
     * Updates flow and waits for it to become UP
     */
    FlowResponseV2 updateFlow(String flowId, FlowRequestV2 flow) {
        log.debug("Updating flow '${flowId}'")
        def response = northboundV2.updateFlow(flowId, flow)
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flowId).status == FlowState.UP
            assert northbound.getFlowHistory(flowId).last().payload.last().action == UPDATE_SUCCESS
        }
        return response
    }

    /**
     * Updates flow and waits for it to become UP
     */
    FlowResponseV2 updateFlow(String flowId, FlowCreatePayload flow) {
        return updateFlow(flowId, toV2(flow))
    }

    FlowResponseV2 partialUpdate(String flowId, FlowPatchV2 flow) {
        log.debug("Updating flow '${flowId}'(partial update)")
        def response = northboundV2.partialUpdate(flowId, flow)
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flowId).status == FlowState.UP
            assert northbound.getFlowHistory(flowId).last().payload.last().action == UPDATE_SUCCESS
        }
        return response
    }

    FlowMirrorPointResponseV2 createMirrorPoint(String flowId, FlowMirrorPointPayload mirrorPoint) {
        def response = northboundV2.createMirrorPoint(flowId, mirrorPoint)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlow(flowId).mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
            assert northbound.getFlowHistory(flowId).last().payload.last().action == CREATE_MIRROR_SUCCESS
        }
        return response
    }

    String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
    }

    int randomVlan() {
        return randomVlan([])
    }

    int randomVlan(List<Integer> exclusions) {
        return (allowedVlans - exclusions)[random.nextInt(allowedVlans.size())]
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

    static FlowPayload toV1(FlowRequestV2 flow) {
        FlowPayload.builder()
                   .id(flow.flowId)
                   .description(flow.description)
                   .maximumBandwidth(flow.maximumBandwidth)
                   .ignoreBandwidth(flow.ignoreBandwidth)
                   .allocateProtectedPath(flow.allocateProtectedPath)
                   .periodicPings(flow.periodicPings)
                   .encapsulationType(flow.encapsulationType)
                   .maxLatency(flow.maxLatency)
                   .pinned(flow.pinned)
                   .priority(flow.priority)
                   .source(toV1(flow.source))
                   .destination(toV1(flow.destination))
                   .build()
    }

    static FlowEndpointPayload toV1(FlowEndpointV2 ep) {
        new FlowEndpointPayload(ep.switchId, ep.portNumber, ep.vlanId, ep.getInnerVlanId(),
                new DetectConnectedDevicesPayload(false, false))
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

    static FlowRequestV2 toV2(FlowCreatePayload flow) {
        def result = toV2((FlowPayload) flow);
        result.setDiverseFlowId(flow.getDiverseFlowId());
        return result;
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

    static FlowRequestV2 toRequest(FlowResponseV2 flow) {
        return FlowRequestV2.builder()
                            .flowId(flow.flowId)
                            .source(flow.source)
                            .destination(flow.destination)
                            .maximumBandwidth(flow.maximumBandwidth)
                            .ignoreBandwidth(flow.ignoreBandwidth)
                            .periodicPings(flow.periodicPings)
                            .description(flow.description)
                            .maxLatency(flow.maxLatency)
                            .maxLatencyTier2(flow.maxLatencyTier2)
                            .priority(flow.priority)
                            .pinned(flow.pinned)
                            .allocateProtectedPath(flow.allocateProtectedPath)
                            .encapsulationType(flow.encapsulationType)
                            .pathComputationStrategy(flow.pathComputationStrategy)
                            .build()
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param useTraffgenPorts whether to try finding a traffgen port
     */
    private FlowEndpointV2 getFlowEndpoint(Switch sw, boolean useTraffgenPorts = true) {
        getFlowEndpoint(sw, topology.getAllowedPortsForSwitch(sw), useTraffgenPorts)
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param allowedPorts list of ports to randomly choose port from
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
     */
    private FlowEndpointV2 getFlowEndpoint(Switch sw, List<Integer> allowedPorts,
            boolean useTraffgenPorts = true) {
        int port = allowedPorts[random.nextInt(allowedPorts.size())]
        if (useTraffgenPorts) {
            List<Integer> tgPorts = sw.traffGens*.switchPort.findAll { allowedPorts.contains(it) }
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
