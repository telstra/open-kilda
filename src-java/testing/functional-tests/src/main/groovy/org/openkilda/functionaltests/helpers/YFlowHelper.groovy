package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS_Y
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS_Y
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.transform.Memoized
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
class YFlowHelper {
    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    PathHelper pathHelper

    def random = new Random()
    def faker = new Faker()
    def allowedVlans = 101..4095

    /**
     * Creates YFlowCreatePayload for a y-flow with random vlan.
     * Guarantees that different ports are used for shared endpoint and subflow endpoints (given the same switch)
     *
     * @param sharedSwitch the shared endpoint of y-flow
     * @param firstSwitch the endpoint of the first sub-flow
     * @param secondSwitch the endpoint of the second sub-flow
     * @param useTraffgenPorts try using traffgen ports if available
     */
    YFlowCreatePayload randomYFlow(Switch sharedSwitch, Switch firstSwitch, Switch secondSwitch,
                                   boolean useTraffgenPorts = true, List<YFlowCreatePayload> existingFlows = []) {
        List<SwitchPortVlan> busyEndpoints = getBusyEndpoints(existingFlows)
        def se = YFlowSharedEndpoint.builder()
                .switchId(sharedSwitch.dpId)
                .portNumber(randomEndpointPort(sharedSwitch, useTraffgenPorts, busyEndpoints))
                .build()
        def subFlows = [firstSwitch, secondSwitch].collect {sw ->
            def seVlan = randomVlan()
            busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, seVlan)
            def ep = SubFlowUpdatePayload.builder()
                    .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder().vlanId(seVlan).build())
                    .endpoint(randomEndpoint(sw, useTraffgenPorts, busyEndpoints))
                    .build()
            busyEndpoints << new SwitchPortVlan(ep.endpoint.switchId, ep.endpoint.portNumber, ep.endpoint.vlanId)
            ep
        }
        return YFlowCreatePayload.builder()
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

    YFlowCreatePayload randomYFlow(SwitchTriplet swT, boolean useTraffgenPorts = true,
                                   List<YFlowCreatePayload> existingFlows = []) {
        randomYFlow(swT.shared, swT.ep1, swT.ep2, useTraffgenPorts, existingFlows)
    }

    /**
     * Creates YFlowCreatePayload for a single-swith y-flow with random vlan.
     *
     * @param sw the switch for shared and sub-flow endpoints
     * @param useTraffgenPorts try using traffgen ports if available
     */
    YFlowCreatePayload singleSwitchYFlow(Switch sw, boolean useTraffgenPorts = true,
                                         List<YFlowCreatePayload> existingFlows = []) {
        List<SwitchPortVlan> busyEndpoints = getBusyEndpoints(existingFlows)

        def sePort = randomEndpointPort(sw, useTraffgenPorts, busyEndpoints)
        busyEndpoints << new SwitchPortVlan(sw.dpId, sePort)
        def epPort = randomEndpointPort(sw, useTraffgenPorts, busyEndpoints)

        def subFlows = (0..1).collect {
            def payload = Wrappers.retry(3, 0) {
                def seVlan = randomVlan()
                if (busyEndpoints.contains(new SwitchPortVlan(sw.dpId, sePort, seVlan))) {
                    throw new Exception("Generated sub-flow conflicts with existing endpoints.")
                }
                def epVlan = randomVlan();
                if (busyEndpoints.contains(new SwitchPortVlan(sw.dpId, epPort, epVlan))) {
                    throw new Exception("Generated sub-flow conflicts with existing endpoints.")
                }
                SubFlowUpdatePayload.builder()
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder().vlanId(seVlan).build())
                        .endpoint(new FlowEndpointV2(sw.dpId, epPort, epVlan, new DetectConnectedDevicesV2(false, false)))
                        .build()
            }
            busyEndpoints << new SwitchPortVlan(sw.dpId, sePort, payload.sharedEndpoint.vlanId)
            busyEndpoints << new SwitchPortVlan(sw.dpId, epPort, payload.endpoint.vlanId)
            payload
        }

        return YFlowCreatePayload.builder()
                .sharedEndpoint(YFlowSharedEndpoint.builder().switchId(sw.dpId).portNumber(sePort).build())
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
     * Adds y-flow and waits for it to become UP.
     */
    YFlow addYFlow(YFlowCreatePayload flow) {
        log.debug("Adding y-flow")
        def response = northboundV2.addYFlow(flow)
        assert response.YFlowId
        YFlow yFlow
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            yFlow = northboundV2.getYFlow(response.YFlowId)
            assert yFlow
            assert yFlow.status == FlowState.UP.toString()
            assert northbound.getFlowHistory(response.YFlowId).last().payload.last().action == CREATE_SUCCESS_Y
        }
        return yFlow
    }

    /**
     * Deletes y-flow and waits when the flow disappears from the flow list.
     */
    YFlow deleteYFlow(String yFlowId) {
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert northboundV2.getYFlow(yFlowId)?.status != FlowState.IN_PROGRESS.toString()
        }
        log.debug("Deleting y-flow '$yFlowId'")
        def response = northboundV2.deleteYFlow(yFlowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !northboundV2.getYFlow(yFlowId)
            assert northbound.getFlowHistory(response.YFlowId).last().payload.last().action == DELETE_SUCCESS_Y
        }
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(response.sharedEndpoint.switchId, true)
        return response
    }

    /**
     * Updates y-flow and waits for it to become UP
     */
    YFlow updateYFlow(String yFlowId, YFlowUpdatePayload flow) {
        log.debug("Updating y-flow '${yFlowId}'")
        def response = northboundV2.updateYFlow(yFlowId, flow)
        YFlow yFlow
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            yFlow = northboundV2.getYFlow(response.YFlowId)
            assert yFlow.status == FlowState.UP.toString()
            yFlow.subFlows.each {
                assert it.status == FlowState.UP.toString()
            }
        }
        return yFlow
    }

    YFlow partialUpdateYFlow(String yFlowId, YFlowPatchPayload flow) {
        log.debug("Updating y-flow '${yFlowId}'(partial update)")
        def response = northboundV2.partialUpdateYFlow(yFlowId, flow)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            def yFlow = northboundV2.getYFlow(yFlowId)
            assert yFlow.status == FlowState.UP.toString()
            yFlow.subFlows.each {
                assert it.status == FlowState.UP.toString()
            }
        }
        return response
    }

    @Memoized
    List<Switch> findPotentialYPoints(SwitchTriplet swT) {
        def sortedEp1Paths = swT.pathsEp1.sort { it.size() }
        def potentialEp1Paths = sortedEp1Paths.takeWhile { it.size() == sortedEp1Paths[0].size() }
        def potentialEp2Paths = potentialEp1Paths.collect { potentialEp1Path ->
            def sortedEp2Paths = swT.pathsEp2.sort {
                it.size() - it.intersect(potentialEp1Path).size()
            }
            [path1: potentialEp1Path,
             potentialPaths2: sortedEp2Paths.takeWhile {it.size() == sortedEp2Paths[0].size() }]
        }
        return potentialEp2Paths.collectMany {path1WithPath2 ->
            path1WithPath2.potentialPaths2.collect { List<PathNode> potentialPath2 ->
                def switches = pathHelper.getInvolvedSwitches(path1WithPath2.path1)
                        .intersect(pathHelper.getInvolvedSwitches(potentialPath2))
                switches ? switches[-1] : null
            }
        }.findAll().unique()
    }

    static List<SwitchPortVlan> getBusyEndpoints(List<YFlowCreatePayload> yFlows) {
        yFlows.collectMany { yFlow ->
            yFlow.subFlows.collectMany { subFlow ->
                [new SwitchPortVlan(yFlow.sharedEndpoint.switchId, yFlow.sharedEndpoint.portNumber, subFlow.sharedEndpoint.vlanId),
                 new SwitchPortVlan(subFlow.endpoint.switchId, subFlow.endpoint.portNumber, subFlow.endpoint.vlanId)]
            }
        }
    }

    YFlowUpdatePayload convertToUpdate(YFlow yFlow) {
        def yFlowCopy = yFlow.jacksonCopy()
        def builder = YFlowUpdatePayload.builder()
        YFlowUpdatePayload.class.getDeclaredFields()*.name.each {
            //todo (andriidovhan) rework 'diverseFlowId'. (diverseFlowId - string, diverseWith - set)
            builder.diverseFlowId = yFlowCopy.diverseWithYFlows ? yFlowCopy.diverseWithYFlows[0] : yFlowCopy.diverseWithFlows[0]
            if (yFlowCopy.class.declaredFields*.name.contains(it)) {
                builder."$it" = yFlowCopy."$it"
            }
        }
        return builder.build()
    }

    /**
     * Returns an endpoint with randomly chosen port & vlan.
     *
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
     */
    private FlowEndpointV2 randomEndpoint(Switch sw, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEps) {
        return new FlowEndpointV2(
                sw.dpId, randomEndpointPort(sw, useTraffgenPorts, busyEps), randomVlan(),
                new DetectConnectedDevicesV2(false, false))
    }

    /**
     * Returns a randomly chosen endpoint port for y-flow.
     *
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
     */
    private int randomEndpointPort(Switch sw, boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEps) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw) - busyEps.findAll { it.sw == sw.dpId }*.port
        int port = allowedPorts[random.nextInt(allowedPorts.size())]
        if (useTraffgenPorts) {
            List<Integer> tgPorts = sw.traffGens*.switchPort.findAll { allowedPorts.contains(it) }
            if (tgPorts) {
                port = tgPorts[0]
            } else {
                tgPorts = sw.traffGens*.switchPort.findAll { topology.getAllowedPortsForSwitch(sw).contains(it) }
                if (tgPorts) {
                    port = tgPorts[0]
                }
            }
        }
        return port
    }

    private int randomVlan() {
        return allowedVlans[random.nextInt(allowedVlans.size())]
    }

    private String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest y-flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }
}
