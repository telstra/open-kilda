package org.openkilda.functionaltests.helpers

import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.text.SimpleDateFormat

/**
 * Holds utility methods for manipulating flows supporting version 2 of API.
 */
@Component
@Slf4j
class FlowHelperV2 {
    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northbound
    @Autowired
    NorthboundServiceV2 northboundV2
    @Autowired
    Database db

    def random = new Random()
    def faker = new Faker()
    def allowedVlans = 101..4095

    /**
     * Creates a FlowCreatePayload instance with random vlan and flow id. Will try to build over traffgen ports or use
     * random port otherwise.
     * Since multi-switch and single-switch flows have a bit different algorithms to create a correct flow, this method
     * will delegate the job to the correct algo based on src and dst switches passed.
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
                                       .build()

            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowRequestV2
    }

    /**
     * Creates a FlowCreatePayload instance with random vlan and flow id suitable for a single-switch flow.
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
     * Adds flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely created.
     */
    FlowResponseV2 addFlow(FlowRequestV2 flow) {
        log.debug("Adding flow '${flow.flowId}'")
        def response = northboundV2.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }
        return response
    }

    /**
     * Adds flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely created.
     */
    FlowResponseV2 addFlow(FlowCreatePayload flow) {
        return addFlow(toV2(flow));
    }

    /**
     * Deletes flow with checking rules on source and destination switches.
     * It is supposed if rules absent on source and destination switches, the flow is completely deleted.
     */
    FlowResponseV2 deleteFlow(String flowId) {
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flowId).status != FlowState.IN_PROGRESS }
        log.debug("Deleting flow '$flowId'")
        def response = northboundV2.deleteFlow(flowId)
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.getFlowStatus(flowId) }
        return response
    }

    /**
     * Updates flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely updated.
     */
    FlowResponseV2 updateFlow(String flowId, FlowRequestV2 flow) {
        log.debug("Updating flow '${flowId}'")
        def response = northboundV2.updateFlow(flowId, flow)
        Wrappers.wait(PATH_INSTALLATION_TIME) { assert northbound.getFlowStatus(flowId).status == FlowState.UP }
        return response
    }

    /**
     * Updates flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely updated.
     */
    FlowResponseV2 updateFlow(String flowId, FlowCreatePayload flow) {
        return updateFlow(flowId, toV2(flow))
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
        new FlowEndpointPayload(ep.switchId, ep.portNumber, ep.vlanId, ep.getInnerVlanId(), new DetectConnectedDevicesPayload(false, false))
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
        def port = allowedPorts[random.nextInt(allowedPorts.size())]
        if (useTraffgenPorts) {
            def connectedTraffgens = topology.activeTraffGens.findAll { it.switchConnected == sw }
            if (!connectedTraffgens.empty) {
                port = connectedTraffgens.find { allowedPorts.contains(it.switchPort) }?.switchPort ?: port
            }
        }
        return new FlowEndpointV2(sw.dpId, port, allowedVlans[random.nextInt(allowedVlans.size())])
    }

    /**
     * Generates a unique name for all auto-tests flows.
     */
    private String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
    }

    private String generateDescription() {
        //The health of autotest flows is always questionable
        def descpription = [faker.shakespeare().asYouLikeItQuote(),
                            faker.shakespeare().kingRichardIIIQuote(),
                            faker.shakespeare().romeoAndJulietQuote(),
                            faker.shakespeare().hamletQuote()]
        def r = new Random()
        "autotest flow: ${descpription[r.nextInt(descpription.size())]}"
    }
}
