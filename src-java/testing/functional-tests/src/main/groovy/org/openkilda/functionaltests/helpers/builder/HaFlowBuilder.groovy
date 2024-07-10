package org.openkilda.functionaltests.helpers.builder

import static org.openkilda.functionaltests.helpers.FlowNameGenerator.HA_FLOW
import static org.openkilda.functionaltests.helpers.StringGenerator.generateDescription
import static org.openkilda.functionaltests.helpers.SwitchHelper.getRandomAvailablePort
import static org.openkilda.functionaltests.helpers.SwitchHelper.randomVlan

import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.HaSubFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.util.logging.Slf4j

@Slf4j
class HaFlowBuilder {
    HaFlowExtended haFlowExtended
    private final SUBFLOW_SUFFIX_LIST = ["-a", "-b"]


    /**
     * Creates HaFlowCreatePayload for a ha-flow with random vlan.
     * Guarantees that different ports are used for shared endpoint and subflow endpoints (given the same switch)
     */
    HaFlowBuilder(SwitchTriplet swT,
                  NorthboundServiceV2 northboundV2,
                  TopologyDefinition topologyDefinition,
                  CleanupManager cleanupManager,
                  boolean useTraffgenPorts = true,
                  List<SwitchPortVlan> busyEndpoints = []) {
        this.haFlowExtended = new HaFlowExtended(HA_FLOW.generateId(), northboundV2, topologyDefinition, cleanupManager)

        this.haFlowExtended.sharedEndpoint = HaFlowSharedEndpoint.builder()
                .switchId(swT.shared.dpId)
                .portNumber(getRandomAvailablePort(swT.shared, topologyDefinition, useTraffgenPorts,
                        busyEndpoints.findAll { it.sw == swT.shared.dpId }*.port))
                .vlanId(randomVlan([]))
                .build()
        def se = this.haFlowExtended.sharedEndpoint
        busyEndpoints << new SwitchPortVlan(se.switchId, se.portNumber, se.vlanId)
        this.haFlowExtended.subFlows = [swT.ep1, swT.ep2].collect { sw ->
            HaSubFlowExtended ep = HaSubFlowExtended.builder()
                    .haSubFlowId("${this.haFlowExtended.haFlowId}${SUBFLOW_SUFFIX_LIST.pop()}")
                    .endpointSwitchId(sw.dpId)
                    .endpointPort(getRandomAvailablePort(sw, topologyDefinition, useTraffgenPorts,
                            busyEndpoints.findAll { it.sw == sw.dpId }*.port))
                    .endpointVlan(randomVlan()).build()
            busyEndpoints << new SwitchPortVlan(sw.dpId, ep.endpointPort, ep.endpointVlan)
            return ep
        }
        this.haFlowExtended.description = generateDescription()
        this.haFlowExtended.maximumBandwidth = 1000
        this.haFlowExtended.ignoreBandwidth = false
        this.haFlowExtended.periodicPings = false
        this.haFlowExtended.strictBandwidth = false
        this.haFlowExtended.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        this.haFlowExtended.pathComputationStrategy = PathComputationStrategy.COST
        this.haFlowExtended.diverseWithFlows = [] as Set
        this.haFlowExtended.diverseWithYFlows = [] as Set
        this.haFlowExtended.diverseWithHaFlows = [] as Set
    }

    HaFlowBuilder withProtectedPath(boolean allocateProtectedPath) {
        this.haFlowExtended.allocateProtectedPath = allocateProtectedPath
        return this
    }

    HaFlowBuilder withDiverseFlow(String flowId) {
        this.haFlowExtended.diverseWithFlows.add(flowId)
        return this
    }

    HaFlowBuilder withBandwidth(Integer bandwidth) {
        this.haFlowExtended.maximumBandwidth = bandwidth
        return this
    }

    HaFlowBuilder withPeriodicPing(boolean periodPing) {
        this.haFlowExtended.periodicPings = periodPing
        return this
    }

    HaFlowBuilder withPinned(boolean pinned) {
        this.haFlowExtended.pinned = pinned
        return this
    }

    HaFlowBuilder withEncapsulationType(FlowEncapsulationType encapsulationType) {
        this.haFlowExtended.encapsulationType = encapsulationType
        return this
    }
    HaFlowBuilder withIgnoreBandwidth(boolean ignoreBandwidth) {
        this.haFlowExtended.ignoreBandwidth = ignoreBandwidth
        return this
    }

    HaFlowBuilder withSharedEndpointFullPort() {
        this.haFlowExtended.sharedEndpoint.vlanId = 0
        return this
    }

    HaFlowBuilder withSharedEndpointQnQ() {
        this.haFlowExtended.sharedEndpoint.innerVlanId = new Random().nextInt(4095)
        return this
    }

    HaFlowBuilder withEp1QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.haFlowExtended.subFlows.first().endpointInnerVlan = innerVlan
        return this
    }

    HaFlowBuilder withEp2QnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.haFlowExtended.subFlows.last().endpointInnerVlan = innerVlan
        return this
    }

    HaFlowBuilder withEp1Vlan(Integer vlan = new Random().nextInt(4095)) {
        this.haFlowExtended.subFlows.first().endpointVlan = vlan
        return this
    }

    HaFlowBuilder withEp2Vlan(Integer vlan = new Random().nextInt(4095)) {
        this.haFlowExtended.subFlows.last().endpointVlan = vlan
        return this
    }

    HaFlowBuilder withEp1AndEp2SameQnQ(Integer innerVlan = new Random().nextInt(4095)) {
        this.haFlowExtended.subFlows.first().endpointInnerVlan= innerVlan
        this.haFlowExtended.subFlows.last().endpointInnerVlan= innerVlan
        return this
    }

    HaFlowBuilder withEp1FullPort() {
        this.haFlowExtended.subFlows.first().endpointVlan = 0
        return this
    }

    HaFlowBuilder withEp2FullPort() {
        this.haFlowExtended.subFlows.last().endpointVlan = 0
        return this
    }

    HaFlowBuilder withEp1AndEp2SameSwitchAndPort() {
        this.haFlowExtended.subFlows.first().endpointSwitchId =
                this.haFlowExtended.subFlows.last().endpointSwitchId
        this.haFlowExtended.subFlows.first().endpointPort = this.haFlowExtended.subFlows.last().endpointPort
        return this
    }

    HaFlowBuilder withHaFlowId(String haFlowId) {
        this.haFlowExtended.haFlowId = haFlowId
        return  this
    }
    HaFlowExtended build() {
        return this.haFlowExtended
    }
}