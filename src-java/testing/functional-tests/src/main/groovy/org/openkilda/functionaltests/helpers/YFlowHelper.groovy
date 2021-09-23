package org.openkilda.functionaltests.helpers


import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.messaging.payload.flow.FlowState
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
class YFlowHelper {
    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2

    def random = new Random()
    def faker = new Faker()
    def allowedVlans = 101..4095

    /**
     * Creates YFlowCreatePayload for a y-flow with random vlan.
     *
     * @param sharedSwitch the shared endpoint of y-flow
     * @param firstSwitch the endpoint of the first sub-flow
     * @param secondSwitch the endpoint of the second sub-flow
     * @param useTraffgenPorts try using traffgen ports if available
     */
    YFlowCreatePayload randomYFlow(Switch sharedSwitch, Switch firstSwitch, Switch secondSwitch,
                                   boolean useTraffgenPorts = true) {
        assert srcSwitch.dpId != dstSwitch.dpId

        Wrappers.retry(3, 0) {
            def newFlow = YFlowCreatePayload.builder()
                    .sharedEndpoint(YFlowSharedEndpoint.builder()
                            .switchId(sharedSwitch.dpId)
                            .portNumber(randomEndpointPort(sharedSwitch, useTraffgenPorts))
                            .build())
                    .subFlows([firstSwitch, secondSwitch].collect {
                        SubFlowUpdatePayload.builder()
                                .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder().vlanId(randomVlan()).build())
                                .endpoint(randomEndpoint(it))
                                .build()
                    })
                    .maximumBandwidth(500)
                    .ignoreBandwidth(false)
                    .periodicPings(false)
                    .description(generateDescription())
                    .strictBandwidth(false)
                    .build()

            /*TODO: if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }*/
            return newFlow
        } as YFlowCreatePayload
    }

    /**
     * Adds y-flow and waits for it to become UP.
     */
    YFlow addYFlow(YFlowCreatePayload flow) {
        log.debug("Adding y-flow: '${flow}'")
        def response = northboundV2.addYFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(response.flowId).status == FlowState.UP }
        return response
    }

    /**
     * Deletes y-flow and waits when the flow disappears from the flow list.
     */
    YFlow deleteYFlow(String yFlowId) {
        Wrappers.wait(WAIT_OFFSET * 2) { assert northboundV2.getFlowStatus(yFlowId).status != FlowState.IN_PROGRESS }
        log.debug("Deleting y-flow '$yFlowId'")
        def response = northboundV2.deleteYFlow(yFlowId)
        Wrappers.wait(WAIT_OFFSET) { assert !northboundV2.getFlowStatus(yFlowId) }
        return response
    }

    /**
     * Updates y-flow and waits for it to become UP
     */
    YFlow updateYFlow(String yFlowId, YFlowUpdatePayload flow) {
        log.debug("Updating y-flow '${yFlowId}'")
        def response = northboundV2.updateYFlow(yFlowId, flow)
        Wrappers.wait(PATH_INSTALLATION_TIME) { assert northboundV2.getFlowStatus(yFlowId).status == FlowState.UP }
        return response
    }

    YFlow partialUpdateYFlow(String yFlowId, YFlowPatchPayload flow) {
        log.debug("Updating y-flow '${yFlowId}'(partial update)")
        def response = northboundV2.partialUpdateYFlow(yFlowId, flow)
        Wrappers.wait(PATH_INSTALLATION_TIME) { assert northboundV2.getFlowStatus(yFlowId).status == FlowState.UP }
        return response
    }

    /**
     * Returns an endpoint with randomly chosen port & vlan.
     *
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
     */
    private FlowEndpointV2 randomEndpoint(Switch sw, boolean useTraffgenPorts = true) {
        return new FlowEndpointV2(
                sw.dpId, randomEndpointPort(sw, useTraffgenPorts), randomVlan(),
                new DetectConnectedDevicesV2(false, false))
    }

    /**
     * Returns a randomly chosen endpoint port for y-flow.
     *
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
     */
    private int randomEndpointPort(Switch sw, boolean useTraffgenPorts = true) {
        def allowedPorts = topology.getAllowedPortsForSwitch(sw)
        int port = allowedPorts[random.nextInt(allowedPorts.size())]
        if (useTraffgenPorts) {
            List<Integer> tgPorts = sw.traffGens*.switchPort.findAll { allowedPorts.contains(it) }
            if (tgPorts) {
                port = tgPorts[0]
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
