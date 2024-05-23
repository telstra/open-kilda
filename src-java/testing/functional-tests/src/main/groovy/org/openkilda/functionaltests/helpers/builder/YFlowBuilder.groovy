package org.openkilda.functionaltests.helpers.builder

import static org.openkilda.functionaltests.helpers.FlowHelperV2.availableVlanList
import static org.openkilda.functionaltests.helpers.FlowHelperV2.randomVlan
import static org.openkilda.functionaltests.helpers.FlowNameGenerator.Y_FLOW
import static org.openkilda.functionaltests.helpers.StringGenerator.generateDescription
import static org.openkilda.functionaltests.helpers.SwitchHelper.getRandomAvailablePort
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.PathComputationStrategy.COST

import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.transform.ToString
import groovy.util.logging.Slf4j

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

        def subFlows
        //single switch Y-Flow
        if (swT.isSingleSwitch()) {
            busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber)
            def epPort = getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts, busyEndpoints*.port)
            def availableVlanList = availableVlanList(busyEndpoints*.vlan).shuffled()
            subFlows = [swT.ep1, swT.ep2].collect { sw ->
                def seVlan = availableVlanList.get(new Random().nextInt(availableVlanList.size()))
                def ep = SubFlowUpdatePayload.builder()
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder().vlanId(seVlan).build())
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(sw.dpId)
                                .portNumber(epPort)
                                .vlanId(availableVlanList.get(new Random().nextInt(availableVlanList.size())))
                                .build())
                        .build()
                ep
            }
        } else {
            subFlows = [swT.ep1, swT.ep2].collect { sw ->
                def seVlan = randomVlan(busyEndpoints*.vlan)
                busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, seVlan)
                def ep = SubFlowUpdatePayload.builder()
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder().vlanId(seVlan).build())
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(sw.dpId)
                                .portNumber(getRandomAvailablePort(sw, topologyDefinition, useTraffgenPorts,
                                        busyEndpoints.findAll { it.sw == sw.dpId }*.port))
                                .vlanId(randomVlan(busyEndpoints*.vlan))
                                .build())
                        .build()
                busyEndpoints << new SwitchPortVlan(ep.endpoint.switchId, ep.endpoint.portNumber, ep.endpoint.vlanId)
                ep
            }
        }
        String flowId = Y_FLOW.generateId()
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

    YFlowBuilder withMaxLatency(Long latency) {
        this.yFlowRequest.maxLatency = latency
        return this
    }

    YFlowBuilder withMaxLatencyTier2(Long latency) {
        this.yFlowRequest.maxLatencyTier2 = latency
        return this
    }

    YFlowBuilder withEncapsulationType(FlowEncapsulationType encapsulationType) {
        this.yFlowRequest.encapsulationType = encapsulationType.toString()
        return this
    }

    YFlowBuilder withDiverseFlow(String flowId) {
        this.yFlowRequest.diverseFlowId = flowId
        return this
    }

    YFlowBuilder withPeriodicPings(boolean periodicPings) {
        this.yFlowRequest.periodicPings = periodicPings
        return this
    }

    YFlowBuilder withBandwidth(Long bandwidth) {
        this.yFlowRequest.maximumBandwidth = bandwidth
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

    YFlowBuilder withSharedEpQnQ(Integer innerVlan1 = new Random().nextInt(4095), Integer innerVlan2 = new Random().nextInt(4095)) {
        this.yFlowRequest.subFlows.first().sharedEndpoint.innerVlanId = innerVlan1
        this.yFlowRequest.subFlows.last().sharedEndpoint.innerVlanId = innerVlan2
        return this
    }


    YFlowBuilder withEp1AndEp2SameSwitchAndPort() {
        this.yFlowRequest.subFlows.first().endpoint.switchId = this.yFlowRequest.subFlows.last().endpoint.switchId
        this.yFlowRequest.subFlows.first().endpoint.portNumber = this.yFlowRequest.subFlows.last().endpoint.portNumber
        return this
    }


    YFlowBuilder withSameSharedEndpointsVlan() {
        this.yFlowRequest.subFlows.last().sharedEndpoint.vlanId = this.yFlowRequest.subFlows.first().sharedEndpoint.vlanId
        return this
    }

    YFlowBuilder withSharedEndpointsVlan(Integer vlanId1, Integer vlanId2) {
        this.yFlowRequest.subFlows.first().sharedEndpoint.vlanId = vlanId1
        this.yFlowRequest.subFlows.last().sharedEndpoint.vlanId = vlanId2
        return this
    }

    YFlowBuilder withSubFlow2SharedEp(Integer vlan) {
        this.yFlowRequest.subFlows.last().sharedEndpoint.vlanId = vlan
        return this
    }

    YFlowBuilder withEp1Vlan(Integer vlan) {
        this.yFlowRequest.subFlows.first().endpoint.vlanId = vlan
        return this
    }

    YFlowBuilder withEp1AndEp2Vlan(Integer subFlow1Vlan, Integer subFlow2Vlan) {
        this.yFlowRequest.subFlows.first().endpoint.vlanId = subFlow1Vlan
        this.yFlowRequest.subFlows.last().endpoint.vlanId = subFlow2Vlan
        return this
    }

    YFlowBuilder withSharedEpPort(Integer portNumber) {
        this.yFlowRequest.sharedEndpoint.portNumber = portNumber
        return this
    }

    YFlowBuilder withEp2Port(Integer portNumber) {
        this.yFlowRequest.subFlows.last().endpoint.portNumber = portNumber
        return this
    }

    YFlowBuilder withEp1VlanSameAsEp2Vlan() {
        this.yFlowRequest.subFlows.first().endpoint.vlanId = this.yFlowRequest.subFlows.last().endpoint.vlanId
        return this
    }

    YFlowBuilder withEp2QnqAsEp1Vlan() {
        this.yFlowRequest.subFlows.last().endpoint.innerVlanId = this.yFlowRequest.subFlows.first().endpoint.vlanId
        this.yFlowRequest.subFlows.last().endpoint.vlanId = 0
        this.yFlowRequest.subFlows.first().endpoint.innerVlanId = 0
        return this
    }

    YFlowBuilder withSubFlow1SharedEpQnqAsSubFlow2SharedEpVlan() {
        this.yFlowRequest.subFlows.last().sharedEndpoint.innerVlanId = this.yFlowRequest.subFlows.first().sharedEndpoint.vlanId
        this.yFlowRequest.subFlows.last().sharedEndpoint.vlanId = 0
        this.yFlowRequest.subFlows.first().sharedEndpoint.innerVlanId = 0
        return this
    }

    YFlowBuilder withEp1OnISLPort() {
        def islPort = topologyDefinition.getBusyPortsForSwitch(yFlowRequest.subFlows.first().endpoint.switchId).first()
        this.yFlowRequest.subFlows.first().endpoint.portNumber = islPort
        return this
    }

    YFlowBuilder withSharedEpOnISLPort() {
        def islPort = topologyDefinition.getBusyPortsForSwitch(yFlowRequest.subFlows.first().endpoint.switchId).first()
        this.yFlowRequest.sharedEndpoint.portNumber = islPort
        return this
    }

    YFlowExtended build() {
        log.debug("Adding Y-Flow")
        def yFlow = northboundV2.addYFlow(yFlowRequest)
        new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition)
    }
}
