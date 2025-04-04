package org.openkilda.functionaltests.helpers.builder

import static org.openkilda.functionaltests.helpers.FlowNameGenerator.FLOW
import static org.openkilda.functionaltests.helpers.StringGenerator.generateDescription
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.randomVlan

import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowStatistics
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.util.logging.Slf4j

@Slf4j
class FlowBuilder {

    FlowExtended flowExtended

    FlowBuilder(SwitchExtended srcSwitch,
                SwitchExtended dstSwitch,
                NorthboundService northbound,
                NorthboundServiceV2 northboundV2,
                TopologyDefinition topologyDefinition,
                CleanupManager cleanupManager,
                Database database,
                boolean useTraffgenPorts = true,
                List<SwitchPortVlan> busyEndpoints = []) {

        this.flowExtended = new FlowExtended(FLOW.generateId(), northbound, northboundV2, topologyDefinition, cleanupManager, database)

        this.flowExtended.source = FlowEndpointV2.builder()
                .switchId(srcSwitch.switchId)
                .portNumber(srcSwitch.getRandomPortNumber(useTraffgenPorts, busyEndpoints.findAll { it.sw == srcSwitch.switchId }.port))
                .vlanId(randomVlan(busyEndpoints*.vlan))
                .detectConnectedDevices(new DetectConnectedDevicesV2(false, false)).build()

        if (srcSwitch == dstSwitch) {
            // For a SingleSwitch flow, selected switch should have >=2 traffGens
            busyEndpoints << new SwitchPortVlan(flowExtended.source.switchId, flowExtended.source.portNumber, flowExtended.source.vlanId)
        }

        this.flowExtended.destination = FlowEndpointV2.builder()
                .switchId(dstSwitch.switchId)
                .portNumber(dstSwitch.getRandomPortNumber(useTraffgenPorts, busyEndpoints.findAll { it.sw == dstSwitch.switchId }.port))
                .vlanId(randomVlan(busyEndpoints*.vlan))
                .detectConnectedDevices(new DetectConnectedDevicesV2(false, false)).build()

        this.flowExtended.description = generateDescription()
        this.flowExtended.maximumBandwidth = 500
        this.flowExtended.ignoreBandwidth = false
        this.flowExtended.periodicPings = false
        this.flowExtended.strictBandwidth = false
    }

    FlowExtended build() {
        this.flowExtended
    }

    FlowBuilder withProtectedPath(boolean allocateProtectedPath) {
        this.flowExtended.allocateProtectedPath = allocateProtectedPath
        return this
    }

    FlowBuilder withSourceSwitch(SwitchId switchId) {
        this.flowExtended.source.switchId = switchId
        return this
    }

    FlowBuilder withSourcePort(Integer portNumber) {
        this.flowExtended.source.portNumber = portNumber
        return this
    }

    FlowBuilder withSourceVlan(Integer vlan) {
        this.flowExtended.source.vlanId = vlan
        return this
    }

    FlowBuilder withSourceInnerVlan(Integer innerVlan) {
        this.flowExtended.source.innerVlanId = innerVlan
        return this
    }

    FlowBuilder withDestinationSwitch(SwitchId switchId) {
        this.flowExtended.destination.switchId = switchId
        return this
    }

    FlowBuilder withDestinationPort(Integer portNumber) {
        this.flowExtended.destination.portNumber = portNumber
        return this
    }

    FlowBuilder withDestinationVlan(Integer vlan) {
        this.flowExtended.destination.vlanId = vlan
        return this
    }

    FlowBuilder withDestinationInnerVlan(Integer innerVlan) {
        this.flowExtended.destination.innerVlanId = innerVlan
        return this
    }

    FlowBuilder withSamePortOnSourceAndDestination() {
        this.flowExtended.source.portNumber =  this.flowExtended.destination.portNumber
        return this
    }

    FlowBuilder withSameVlanOnSourceAndDestination() {
        this.flowExtended.source.vlanId =  this.flowExtended.destination.vlanId
        return this
    }

    FlowBuilder withBandwidth(Long bandwidth) {
        this.flowExtended.maximumBandwidth = bandwidth
        return this
    }

    FlowBuilder withFlowId(String id) {
        this.flowExtended.flowId = id
        return this
    }

    FlowBuilder withMaxLatency(Long latency) {
        this.flowExtended.maxLatency = latency
        return this
    }

    FlowBuilder withPathComputationStrategy(PathComputationStrategy pathComputationStrategy) {
        this.flowExtended.pathComputationStrategy = pathComputationStrategy
        return this
    }

    FlowBuilder withEncapsulationType(FlowEncapsulationType encapsulationType) {
        this.flowExtended.encapsulationType = encapsulationType
        return this
    }

    FlowBuilder withStatistics(FlowStatistics stats) {
        this.flowExtended.statistics = stats
        return this
    }

    FlowBuilder withPriority(Integer priority){
        this.flowExtended.priority = priority
        return this
    }

    FlowBuilder withMaxLatencyTier2(Long latencyTier2){
        this.flowExtended.maxLatencyTier2 = latencyTier2
        return this
    }

    FlowBuilder withDescription(String description){
        this.flowExtended.description = description
        return this
    }

    FlowBuilder withPeriodicPing(boolean periodicPing) {
        this.flowExtended.periodicPings = periodicPing
        return this
    }

    FlowBuilder withIgnoreBandwidth(boolean ignoreBandwidth) {
        this.flowExtended.ignoreBandwidth = ignoreBandwidth
        return this
    }

    FlowBuilder withPinned(boolean pinned) {
        this.flowExtended.pinned = pinned
        return this
    }

    FlowBuilder withStrictBandwidth(boolean strictBandwidth) {
        this.flowExtended.strictBandwidth = strictBandwidth
        return this
    }

    FlowBuilder withDetectedDevicesOnDst(boolean lldp, boolean arp) {
        this.flowExtended.destination.detectConnectedDevices = new DetectConnectedDevicesV2(lldp, arp)
        return this
    }

    FlowBuilder withDetectedDevicesOnSrc(boolean lldp, boolean arp) {
        this.flowExtended.source.detectConnectedDevices = new DetectConnectedDevicesV2(lldp, arp)
        return this
    }

    FlowBuilder withAffinityFlow(String flowId) {
        this.flowExtended.affinityWith = flowId
        return this
    }

    FlowBuilder withDiverseFlow(String flowId) {
        this.flowExtended.diverseWith = [flowId]
        return this
    }
}
