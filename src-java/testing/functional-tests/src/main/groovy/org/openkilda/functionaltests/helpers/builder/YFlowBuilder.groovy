package org.openkilda.functionaltests.helpers.builder

import static org.openkilda.functionaltests.helpers.FlowHelperV2.randomVlan
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS_Y
import static org.openkilda.functionaltests.helpers.SwitchHelper.getRandomAvailablePort
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.PathComputationStrategy.COST
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.transform.ToString
import groovy.util.logging.Slf4j

import java.text.SimpleDateFormat

@Slf4j
@ToString(includeNames = true, excludes = 'northbound, northboundV2, topologyDefinition')
class YFlowBuilder {

    YFlowCreatePayload yFlowRequest

    NorthboundService northbound
    NorthboundServiceV2 northboundV2
    TopologyDefinition topologyDefinition

    static def random = new Random()
    static def faker = new Faker()

    YFlowBuilder(SwitchTriplet swT, NorthboundService northbound, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition,
                 boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition

        def se = YFlowSharedEndpoint.builder()
                .switchId(swT.shared.dpId)
                .portNumber(getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts,
                        busyEndpoints.findAll { it.sw == swT.shared.dpId }*.port))
                .build()
        def subFlows = [swT.ep1, swT.ep2].collect { sw ->
            def seVlan = randomVlan(busyEndpoints*.vlan)
            busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, seVlan)
            def ep = SubFlowUpdatePayload.builder()
                    .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder().vlanId(seVlan).build())
                    .endpoint(FlowEndpointV2.builder()
                            .switchId(sw.dpId)
                            .portNumber(getRandomAvailablePort(sw, topologyDefinition, useTraffgenPorts,
                                    busyEndpoints.findAll { it.sw == swT.shared.dpId }*.port))
                            .vlanId(randomVlan(busyEndpoints*.vlan))
                            .build())
                    .build()
            busyEndpoints << new SwitchPortVlan(ep.endpoint.switchId, ep.endpoint.portNumber, ep.endpoint.vlanId)
            ep
        }
        String flowId = generateFlowId()
        subFlows.first().flowId = "S1." + flowId
        subFlows.last().flowId = "S2." + flowId
        this.yFlowRequest = YFlowCreatePayload.builder()
                .yFlowId(flowId)
                .sharedEndpoint(se)
                .subFlows(subFlows)
                .maximumBandwidth(1000)
                .ignoreBandwidth(false)
                .periodicPings(false)
                .description(generateDescription())
                .strictBandwidth(false)
                .encapsulationType(TRANSIT_VLAN.toString())
                .pathComputationStrategy(COST.toString())
                .build()
    }

    YFlowBuilder withProtectedPath(boolean allocateProtectedPath) {
        this.yFlowRequest.allocateProtectedPath = allocateProtectedPath
        return this
    }

    YFlowBuilder withEp1FullPort() {
        this.yFlowRequest.subFlows.first().endpoint.vlanId = 0
        return this
    }

    YFlowBuilder withEp2FullPort() {
        this.yFlowRequest.subFlows.last().endpoint.vlanId = 0
        return this
    }


    YFlowBuilder withEncapsulationType(FlowEncapsulationType encapsulationType) {
        this.yFlowRequest.encapsulationType = encapsulationType.toString()
        return this
    }

    YFlowBuilder withEp1QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.yFlowRequest.subFlows.first().endpoint.innerVlanId = innerVlan
        return this
    }

    YFlowBuilder withEp2QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.yFlowRequest.subFlows.last().endpoint.innerVlanId = innerVlan
        return this
    }

    YFlowBuilder withSharedEpQnQ() {
        this.yFlowRequest.subFlows.last().sharedEndpoint.innerVlanId = new Random().nextInt(4095)
        this.yFlowRequest.subFlows.first().sharedEndpoint.innerVlanId = new Random().nextInt(4095)
        return this
    }

    YFlowBuilder withEp1AndEp2SameSwitchAndPort() {
        this.yFlowRequest.subFlows.first().endpoint.switchId = this.yFlowRequest.subFlows.last().endpoint.switchId
        this.yFlowRequest.subFlows.first().endpoint.portNumber = this.yFlowRequest.subFlows.last().endpoint.portNumber
        return this
    }

    /**
     * Adds Y-Flow and waits for it to become UP.
     */
    YFlowExtended build() {
        log.debug("Adding Y-Flow")
        def response = northboundV2.addYFlow(yFlowRequest)
        assert response.YFlowId
        YFlow yFlow
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            yFlow = northboundV2.getYFlow(response.YFlowId)
            assert yFlow
            assert yFlow.status == FlowState.UP.toString()
            assert northbound.getFlowHistory(response.YFlowId).last().payload.last().action == CREATE_SUCCESS_Y
        }
        new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition)
    }

    private String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest Y-Flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }

    private String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4) + "_yflow"
    }
}
