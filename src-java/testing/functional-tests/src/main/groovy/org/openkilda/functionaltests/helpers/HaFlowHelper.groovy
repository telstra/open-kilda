package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.helpers.FlowHelperV2.randomVlan
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult

import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.payload.flow.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowStatus
import org.openkilda.model.PathComputationStrategy
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlow
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowCreatePayload
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

/**
 * Holds utility methods for manipulating y-flows.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class HaFlowHelper {
    @Autowired
    TopologyDefinition topology
    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    HaPathHelper haPathHelper
    @Autowired
    FlowHelperV2 flowHelperV2

    def random = new Random()
    def faker = new Faker()

    /**
     * Creates HaFlowCreatePayload for a ha-flow with random vlan.
     * Guarantees that different ports are used for shared endpoint and subflow endpoints (given the same switch)
     *
     * @param sharedSwitch the shared endpoint of y-flow
     * @param firstSwitch the endpoint of the first sub-flow
     * @param secondSwitch the endpoint of the second sub-flow
     */
    HaFlowCreatePayload randomHaFlow(
            Switch sharedSwitch, Switch firstSwitch, Switch secondSwitch, List<HaFlowCreatePayload> existingFlows = []) {
        List<SwitchPortVlan> busyEndpoints = getBusyEndpoints(existingFlows)
        def se = HaFlowSharedEndpoint.builder()
                .switchId(sharedSwitch.dpId)
                .portNumber(randomEndpointPort(sharedSwitch, busyEndpoints))
                .vlanId(randomVlan([]))
                .build()
        def subFlows = [firstSwitch, secondSwitch].collect { sw ->
            busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, se.vlanId)
            def ep = HaSubFlowCreatePayload.builder()
                    .endpoint(randomEndpoint(sw, busyEndpoints))
                    .build()
            busyEndpoints << new SwitchPortVlan(ep.endpoint.switchId, ep.endpoint.portNumber, ep.endpoint.vlanId)
            ep
        }
        return HaFlowCreatePayload.builder()
                .haFlowId(flowHelperV2.generateFlowId())
                .sharedEndpoint(se)
                .subFlows(subFlows)
                .maximumBandwidth(1000)
                .ignoreBandwidth(false)
                .periodicPings(false)
                .description(generateDescription())
                .strictBandwidth(false)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.toString())
                .pathComputationStrategy(PathComputationStrategy.COST.toString())
                .build()
    }

    HaFlowCreatePayload randomHaFlow(SwitchTriplet swT, List<HaFlowCreatePayload> existingFlows = []) {
        randomHaFlow(swT.shared, swT.ep1, swT.ep2, existingFlows)
    }

    /**
     * Creates YFlowCreatePayload for a single-swith y-flow with random vlan.
     *
     * @param sw the switch for shared and sub-flow endpoints
     * @param useTraffgenPorts try using traffgen ports if available
     */
    HaFlowCreatePayload singleSwitchHaFlow(Switch sw, List<HaFlowCreatePayload> existingFlows = []) {
        List<SwitchPortVlan> busyEndpoints = getBusyEndpoints(existingFlows)

        def sePort = randomEndpointPort(sw, busyEndpoints)
        busyEndpoints << new SwitchPortVlan(sw.dpId, sePort)
        def epPort = randomEndpointPort(sw, busyEndpoints)

        def seVlan = randomVlan()
        def subFlows = (0..1).collect {
            def payload = Wrappers.retry(3, 0) {
                if (busyEndpoints.contains(new SwitchPortVlan(sw.dpId, sePort, seVlan))) {
                    throw new Exception("Generated sub-flow conflicts with existing endpoints.")
                }
                def epVlan = randomVlan();
                if (busyEndpoints.contains(new SwitchPortVlan(sw.dpId, epPort, epVlan))) {
                    throw new Exception("Generated sub-flow conflicts with existing endpoints.")
                }
                HaSubFlowCreatePayload.builder()
                        .endpoint(new BaseFlowEndpointV2(sw.dpId, epPort, epVlan, 0))
                        .build()
            }
            busyEndpoints << new SwitchPortVlan(sw.dpId, epPort, payload.endpoint.vlanId)
            payload
        }

        return HaFlowCreatePayload.builder()
                .sharedEndpoint(HaFlowSharedEndpoint.builder().switchId(sw.dpId).portNumber(sePort).vlanId(seVlan).build())
                .subFlows(subFlows)
                .maximumBandwidth(1000)
                .ignoreBandwidth(false)
                .periodicPings(false)
                .description(generateDescription())
                .strictBandwidth(false)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.toString())
                .pathComputationStrategy(PathComputationStrategy.COST.toString())
                .build()
    }

    /**
     * Adds ha-flow and waits for it to become UP.
     */
    HaFlow addHaFlow(HaFlowCreatePayload flow) {
        log.debug("Adding ha-flow")
        def response = northboundV2.addHaFlow(flow)
        assert response.haFlowId
        HaFlow haFlow
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            haFlow = northboundV2.getHaFlow(response.haFlowId)
            assertHaFlowAndSubFlowStatuses(haFlow, FlowState.UP)
        }
        haFlow
    }

    /**
     * Sends manual reroute request for HA-flow
     */
    HaFlowRerouteResult rerouteHaFlow(String haFlowId) {
        northboundV2.rerouteHaFlow(haFlowId)
    }

    /**
     * Gets status of HA-flow
     */
    String getHaFlowStatus(String haFlowId) {
        northboundV2.getHaFlow(haFlowId).status
    }

    /**
     * Deletes y-flow and waits when the flow disappears from the flow list.
     */
    HaFlow deleteHaFlow(String haFlowId) {
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert northboundV2.getHaFlow(haFlowId)?.status != FlowState.IN_PROGRESS.toString()
        }
        log.debug("Deleting ha-flow '$haFlowId'")
        def response = northboundV2.deleteHaFlow(haFlowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !northboundV2.getHaFlow(haFlowId)
        }
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(response.sharedEndpoint.switchId, true)
        return response
    }

    /**
     * Checks if status of HA-flow and statuses of HA-sub flows are equal to expected
     */
    void assertHaFlowAndSubFlowStatuses(String haFlowId, FlowState expectedStatus) {
        assertHaFlowAndSubFlowStatuses(northboundV2.getHaFlow(haFlowId), expectedStatus)
    }

    /**
     * Checks if status of HA-flow and statuses of HA-sub flows are equal to expected
     */
    void assertHaFlowAndSubFlowStatuses(HaFlow haFlow, FlowState expectedStatus) {
        assert haFlow
        assert haFlow.status == expectedStatus.toString()
                && haFlow.getSubFlows().get(0).status == expectedStatus.toString()
                && haFlow.getSubFlows().get(1).status == expectedStatus.toString(),
                "Flow: ${haFlow}\nPaths: ${northboundV2.getHaFlowPaths(haFlow.getHaFlowId())}"
    }

    /**
     * Updates y-flow and waits for it to become UP
     */
    HaFlow updateHaFlow(String haFlowId, HaFlowUpdatePayload flow) {
        log.debug("Updating ha-flow '${haFlowId}'")
        def response = northboundV2.updateHaFlow(haFlowId, flow)
        HaFlow haFlow
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            haFlow = northboundV2.getHaFlow(response.haFlowId)
            assert haFlow.status == FlowState.UP.toString()
            haFlow.subFlows.each {
                assert it.status == FlowState.UP.toString()
            }
        }
        haFlow
    }

    HaFlow partialUpdateHaFlow(String haFlowId, HaFlowPatchPayload flow) {
        log.debug("Updating ha-flow '${haFlowId}'(partial update)")
        def response = northboundV2.partialUpdateHaFlow(haFlowId, flow)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            def haFlow = northboundV2.getHaFlow(haFlowId)
            assert haFlow.status == FlowState.UP.toString()
            haFlow.subFlows.each {
                assert it.status == FlowState.UP.toString()
            }
        }
        response
    }

    Set<SwitchId> getInvolvedSwitches(String haFlowId) {
        return getInvolvedSwitches(northboundV2.getHaFlowPaths(haFlowId))
    }

    Set<SwitchId> getInvolvedSwitches(HaFlowPaths haFlowPaths) {
        return (List<SwitchId>) haPathHelper.getInvolvedIsls(haFlowPaths)
                .collect { [it.getSrcSwitch().getDpId(), it.getDstSwitch().getDpId()] }.flatten().unique()
    }

    HaFlowValidationResult validate(String haFlowId) {
        return northboundV2.validateHaFlow(haFlowId)
    }

    static List<SwitchPortVlan> getBusyEndpoints(List<HaFlowCreatePayload> haFlows) {
        haFlows.collectMany { haFlow ->
            [new SwitchPortVlan(haFlow.sharedEndpoint.switchId, haFlow.sharedEndpoint.portNumber, haFlow.sharedEndpoint.vlanId),
            ] + haFlow.subFlows.collectMany { subFlow ->
                [new SwitchPortVlan(subFlow.endpoint.switchId, subFlow.endpoint.portNumber, subFlow.endpoint.vlanId)]
            }
        }
    }

    static HaFlowUpdatePayload convertToUpdate(HaFlow haFlow) {
        def haFlowCopy = haFlow.jacksonCopy()
        def builder = HaFlowUpdatePayload.builder()
        HaFlowUpdatePayload.class.getDeclaredFields()*.name.each {
            builder.diverseFlowId(getDiverseFlowId(haFlowCopy))
            if (haFlowCopy.class.declaredFields*.name.contains(it)) {
                builder."$it" = haFlowCopy."$it"
            }
        }
        return builder.build()
    }

    static String getDiverseFlowId(HaFlow haFlow) {
        if (haFlow.diverseWithFlows) {
            return haFlow.diverseWithFlows[0]
        } else if (haFlow.diverseWithYFlows) {
            return haFlow.diverseWithYFlows[0]
        } else if (haFlow.diverseWithHaFlows) {
            return haFlow.diverseWithHaFlows[0]
        } else {
            return null;
        }
    }

    SwitchId getYPoint(HaFlow haFlow) {
        def sharedForwardPath = northboundV2.getHaFlowPaths(haFlow.getHaFlowId()).getSharedPath().getForward()
        return sharedForwardPath != null ? sharedForwardPath.last().getSwitchId() :
                haFlow.getSharedEndpoint().getSwitchId()
    }

    /**
     * Returns an endpoint with randomly chosen port & vlan.
     */
    private FlowEndpointV2 randomEndpoint(Switch sw, List<SwitchPortVlan> busyEps) {
        return new FlowEndpointV2(
                sw.dpId, randomEndpointPort(sw, busyEps), randomVlan(),
                new DetectConnectedDevicesV2(false, false))
    }

    /**
     * Returns a randomly chosen endpoint port for ha-flow.
     */
    private int randomEndpointPort(Switch sw, List<SwitchPortVlan> busyEps) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw) - busyEps.findAll { it.sw == sw.dpId }*.port
        allowedPorts[random.nextInt(allowedPorts.size())]
    }

    private String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest HA-Flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }
}
