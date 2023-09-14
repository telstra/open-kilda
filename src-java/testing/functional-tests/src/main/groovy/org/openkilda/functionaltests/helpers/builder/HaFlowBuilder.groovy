package org.openkilda.functionaltests.helpers.builder

import static org.openkilda.functionaltests.helpers.FlowHelperV2.randomVlan
import static org.openkilda.functionaltests.helpers.SwitchHelper.getRandomAvailablePort
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.payload.flow.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowCreatePayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.transform.ToString
import groovy.util.logging.Slf4j

import java.text.SimpleDateFormat

@Slf4j
@ToString(includeNames = true, excludes = 'northboundV2, topologyDefinition')
class HaFlowBuilder {
    HaFlowCreatePayload haFlowRequest

    NorthboundServiceV2 northboundV2
    TopologyDefinition topologyDefinition

    static def random = new Random()
    static def faker = new Faker()


    /**
     * Creates HaFlowCreatePayload for a ha-flow with random vlan.
     * Guarantees that different ports are used for shared endpoint and subflow endpoints (given the same switch)
     */
    HaFlowBuilder(SwitchTriplet swT, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition,
                  boolean useTraffgenPorts = true, List<Integer> busyEndpoints = []) {
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition

        def se = HaFlowSharedEndpoint.builder()
                .switchId(swT.shared.dpId)
                .portNumber(getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts, busyEndpoints))
                .vlanId(randomVlan([]))
                .build()
        def subFlows = [swT.ep1, swT.ep2].collect { sw ->
            busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, se.vlanId)
            def ep = HaSubFlowCreatePayload.builder()
                    .endpoint(BaseFlowEndpointV2.builder()
                            .switchId(sw.dpId)
                            .portNumber(getRandomAvailablePort(sw, topologyDefinition, useTraffgenPorts, busyEndpoints))
                            .vlanId(randomVlan()).build())
                    .build()
            busyEndpoints << new SwitchPortVlan(ep.endpoint.switchId, ep.endpoint.portNumber, ep.endpoint.vlanId)
            ep
        }
        this.haFlowRequest = HaFlowCreatePayload.builder()
                .haFlowId(generateFlowId())
                .description(generateDescription())
                .sharedEndpoint(se)
                .subFlows(subFlows)
                .maximumBandwidth(1000)
                .ignoreBandwidth(false)
                .periodicPings(false)
                .strictBandwidth(false)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.toString())
                .pathComputationStrategy(PathComputationStrategy.COST.toString()).build()
    }

    HaFlowBuilder withProtectedPath(boolean allocateProtectedPath) {
        this.haFlowRequest.allocateProtectedPath = allocateProtectedPath
        return this
    }

    /**
     * Adds ha-flow and waits for it to become UP.
     */
    HaFlowExtended create() {
        log.debug("Adding ha-flow")
        def response = northboundV2.addHaFlow(haFlowRequest)
        assert response.haFlowId
        HaFlow haFlow
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            haFlow = northboundV2.getHaFlow(response.haFlowId)
            assert haFlow.status == FlowState.UP.toString()
                    && haFlow.getSubFlows().status.unique() == [FlowState.UP.toString()], "Flow: ${haFlow}"
        }
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    private String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest HA-Flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }

    private String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
    }
}