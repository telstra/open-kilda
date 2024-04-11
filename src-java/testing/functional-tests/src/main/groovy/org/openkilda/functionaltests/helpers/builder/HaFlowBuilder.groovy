package org.openkilda.functionaltests.helpers.builder

import static org.openkilda.functionaltests.helpers.FlowHelperV2.randomVlan
import static org.openkilda.functionaltests.helpers.SwitchHelper.getRandomAvailablePort

import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.payload.flow.FlowEncapsulationType
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2
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
                  boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition

        def se = HaFlowSharedEndpoint.builder()
                .switchId(swT.shared.dpId)
                .portNumber(getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts,
                        busyEndpoints.findAll { it.sw == swT.shared.dpId }*.port))
                .vlanId(randomVlan([]))
                .build()
        def subFlows = [swT.ep1, swT.ep2].collect { sw ->
            busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, se.vlanId)
            def ep = HaSubFlowCreatePayload.builder()
                    .endpoint(BaseFlowEndpointV2.builder()
                            .switchId(sw.dpId)
                            .portNumber(getRandomAvailablePort(sw, topologyDefinition, useTraffgenPorts,
                                    busyEndpoints.findAll { it.sw == sw.dpId }*.port))
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

    HaFlowBuilder withDiverseFlow(String flowId) {
        this.haFlowRequest.diverseFlowId = flowId
        return this
    }

    HaFlowBuilder withBandwidth(Integer bandwidth) {
        this.haFlowRequest.maximumBandwidth = bandwidth
        return this
    }

    HaFlowBuilder withPeriodicPing(boolean periodPing) {
        this.haFlowRequest.periodicPings = periodPing
        return this
    }

    HaFlowBuilder withPinned(boolean pinned) {
        this.haFlowRequest.pinned = pinned
        return this
    }

    HaFlowBuilder withEncapsulationType(FlowEncapsulationType encapsulationType) {
        this.haFlowRequest.encapsulationType = encapsulationType.toString()
        return this
    }
    HaFlowBuilder withIgnoreBandwidth(boolean ignoreBandwidth) {
        this.haFlowRequest.ignoreBandwidth = ignoreBandwidth
        return this
    }

    HaFlowBuilder withSharedEndpointFullPort() {
        this.haFlowRequest.sharedEndpoint.vlanId = 0
        return this
    }

    HaFlowBuilder withSharedEndpointQnQ() {
        this.haFlowRequest.sharedEndpoint.innerVlanId = new Random().nextInt(4095)
        return this
    }

    HaFlowBuilder withEp1QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.haFlowRequest.subFlows.first().endpoint.innerVlanId = innerVlan
        return this
    }

    HaFlowBuilder withEp2QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.haFlowRequest.subFlows.last().endpoint.innerVlanId = innerVlan
        return this
    }

    HaFlowBuilder withEp1Vlan(Integer vlan = new Random().nextInt(4095)) {
        this.haFlowRequest.subFlows.first().endpoint.vlanId = vlan
        return this
    }

    HaFlowBuilder withEp2Vlan(Integer vlan = new Random().nextInt(4095)) {
        this.haFlowRequest.subFlows.last().endpoint.vlanId = vlan
        return this
    }

    HaFlowBuilder withEp1AndEp2SameQnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.haFlowRequest.subFlows.first().endpoint.innerVlanId= innerVlan
        this.haFlowRequest.subFlows.last().endpoint.innerVlanId= innerVlan
        return this
    }

    HaFlowBuilder withEp1FullPort() {
        this.haFlowRequest.subFlows.first().endpoint.vlanId = 0
        return this
    }

    HaFlowBuilder withEp2FullPort() {
        this.haFlowRequest.subFlows.last().endpoint.vlanId = 0
        return this
    }

    HaFlowBuilder withEp1AndEp2SameSwitchAndPort() {
        this.haFlowRequest.subFlows.first().endpoint.switchId = this.haFlowRequest.subFlows.last().endpoint.switchId
        this.haFlowRequest.subFlows.first().endpoint.portNumber = this.haFlowRequest.subFlows.last().endpoint.portNumber
        return this
    }

    HaFlowExtended build() {
        log.debug("Adding HA-Flow")
        def haFlow = northboundV2.addHaFlow(haFlowRequest)
        return new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    private String generateDescription() {
        def methods = ["asYouLikeItQuote", "kingRichardIIIQuote", "romeoAndJulietQuote", "hamletQuote"]
        sprintf("autotest HA-Flow: %s", faker.shakespeare()."${methods[random.nextInt(methods.size())]}"())
    }

    private String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4) +  "_haflow"
    }
}