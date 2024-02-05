package org.openkilda.functionaltests.helpers.builder

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.northbound.dto.v2.yflows.SubFlow

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
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j

@Slf4j
class YFlowBuilder {

    YFlowExtended yFlow

    static def random = new Random()
    static def faker = new Faker()

    YFlowBuilder(SwitchTriplet swT,
                 NorthboundService northbound,
                 NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition,
                 CleanupManager cleanupManager,
                 boolean useTraffgenPorts = true,
                 List<SwitchPortVlan> busyEndpoints = []) {
        yFlow = new YFlowExtended(Y_FLOW.generateId(),
        northbound,
        northboundV2,
        topologyDefinition,
        cleanupManager)

        yFlow.sharedEndpoint = YFlowSharedEndpoint.builder()
                .switchId(swT.shared.dpId)
                .portNumber(getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts,
                        busyEndpoints.findAll { it.sw == swT.shared.dpId }*.port))
                .build()

        def subFlows
        //single switch Y-Flow
        if (swT.isSingleSwitch()) {
            busyEndpoints << new SwitchPortVlan(yFlow.sharedEndpoint.switchId, yFlow.sharedEndpoint.portNumber)
            def epPort = getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts, busyEndpoints*.port)
            def availableVlanList = availableVlanList(busyEndpoints*.vlan).shuffled()
            subFlows = [swT.ep1, swT.ep2].collect { sw ->
                def seVlan = availableVlanList.get(new Random().nextInt(availableVlanList.size()))
                def ep = SubFlow.builder()
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
                busyEndpoints << new SwitchPortVlan(yFlow.sharedEndpoint.switchId, yFlow.sharedEndpoint.portNumber, seVlan)
                def ep = SubFlow.builder()
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
        subFlows.first().flowId = "S1." + yFlow.yFlowId
        subFlows.last().flowId = "S2." + yFlow.yFlowId
        yFlow.subFlows = subFlows
        yFlow.maximumBandwidth = 1000
        yFlow.ignoreBandwidth = false
        yFlow.periodicPings = false
        yFlow.description = generateDescription()
        yFlow.strictBandwidth = false
        yFlow.encapsulationType = TRANSIT_VLAN
        yFlow.pathComputationStrategy = COST
        yFlow.diverseWithFlows = [] as Set
        yFlow.diverseWithYFlows = [] as Set
        yFlow.diverseWithHaFlows = [] as Set
    }

    YFlowBuilder withProtectedPath(boolean allocateProtectedPath) {
        yFlow.allocateProtectedPath = allocateProtectedPath
        return this
    }

    YFlowBuilder withMaxLatency(Long latency) {
        yFlow.maxLatency = latency
        return this
    }

    YFlowBuilder withMaxLatencyTier2(Long latency) {
        yFlow.maxLatencyTier2 = latency
        return this
    }

    YFlowBuilder withEncapsulationType(FlowEncapsulationType encapsulationType) {
        yFlow.encapsulationType = encapsulationType
        return this
    }

    YFlowBuilder withDiverseFlow(String flowId) {
        yFlow.diverseWithFlows.add(flowId)
        return this
    }

    YFlowBuilder withPeriodicPings(boolean periodicPings) {
        yFlow.periodicPings = periodicPings
        return this
    }

    YFlowBuilder withBandwidth(Long bandwidth) {
        yFlow.maximumBandwidth = bandwidth
        return this
    }

    YFlowBuilder withEp1QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        yFlow.subFlows.first().endpoint.innerVlanId = innerVlan
        return this
    }

    YFlowBuilder withEp2QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        yFlow.subFlows.last().endpoint.innerVlanId = innerVlan
        return this
    }

    YFlowBuilder withSharedEpQnQ(Integer innerVlan1 = new Random().nextInt(4095), Integer innerVlan2 = new Random().nextInt(4095)) {
        yFlow.subFlows.first().sharedEndpoint.innerVlanId = innerVlan1
        yFlow.subFlows.last().sharedEndpoint.innerVlanId = innerVlan2
        return this
    }


    YFlowBuilder withEp1AndEp2SameSwitchAndPort() {
        yFlow.subFlows.first().endpoint.switchId = yFlow.subFlows.last().endpoint.switchId
        yFlow.subFlows.first().endpoint.portNumber = yFlow.subFlows.last().endpoint.portNumber
        return this
    }


    YFlowBuilder withSameSharedEndpointsVlan() {
        yFlow.subFlows.last().sharedEndpoint.vlanId = yFlow.subFlows.first().sharedEndpoint.vlanId
        return this
    }

    YFlowBuilder withSharedEndpointsVlan(Integer vlanId1, Integer vlanId2) {
        yFlow.subFlows.first().sharedEndpoint.vlanId = vlanId1
        yFlow.subFlows.last().sharedEndpoint.vlanId = vlanId2
        return this
    }

    YFlowBuilder withSubFlow2SharedEp(Integer vlan) {
        yFlow.subFlows.last().sharedEndpoint.vlanId = vlan
        return this
    }

    YFlowBuilder withEp1Vlan(Integer vlan) {
        yFlow.subFlows.first().endpoint.vlanId = vlan
        return this
    }

    YFlowBuilder withEp2Vlan(Integer vlan) {
        yFlow.subFlows.last().endpoint.vlanId = vlan
        return this
    }

    YFlowBuilder withEp1AndEp2Vlan(Integer subFlow1Vlan, Integer subFlow2Vlan) {
        yFlow.subFlows.first().endpoint.vlanId = subFlow1Vlan
        yFlow.subFlows.last().endpoint.vlanId = subFlow2Vlan
        return this
    }

    YFlowBuilder withSharedEpPort(Integer portNumber) {
        yFlow.sharedEndpoint.portNumber = portNumber
        return this
    }

    YFlowBuilder withEp2Port(Integer portNumber) {
        yFlow.subFlows.last().endpoint.portNumber = portNumber
        return this
    }

    YFlowBuilder withEp1VlanSameAsEp2Vlan() {
        yFlow.subFlows.first().endpoint.vlanId = yFlow.subFlows.last().endpoint.vlanId
        return this
    }

    YFlowBuilder withEp2QnqAsEp1Vlan() {
        yFlow.subFlows.last().endpoint.innerVlanId = yFlow.subFlows.first().endpoint.vlanId
        yFlow.subFlows.last().endpoint.vlanId = 0
        yFlow.subFlows.first().endpoint.innerVlanId = 0
        return this
    }

    YFlowBuilder withSubFlow1SharedEpQnqAsSubFlow2SharedEpVlan() {
        yFlow.subFlows.last().sharedEndpoint.innerVlanId = yFlow.subFlows.first().sharedEndpoint.vlanId
        yFlow.subFlows.last().sharedEndpoint.vlanId = 0
        yFlow.subFlows.first().sharedEndpoint.innerVlanId = 0
        return this
    }

    YFlowBuilder withEp1OnISLPort() {
        def islPort = yFlow.topologyDefinition.getBusyPortsForSwitch(yFlow.subFlows.first().endpoint.switchId).first()
        yFlow.subFlows.first().endpoint.portNumber = islPort
        return this
    }

    YFlowBuilder withSharedEpOnISLPort() {
        def islPort = yFlow.topologyDefinition.getBusyPortsForSwitch(yFlow.subFlows.first().endpoint.switchId).first()
        yFlow.sharedEndpoint.portNumber = islPort
        return this
    }

    YFlowExtended build() {
        return yFlow
    }
}
