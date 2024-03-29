package org.openkilda.functionaltests.helpers.model

import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.HaFlowBuilder
import org.openkilda.functionaltests.helpers.model.traffic.ha.HaFlowBidirectionalExam
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult
import org.openkilda.northbound.dto.v2.haflows.HaSubFlow
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.northbound.model.HaFlowActionType
import org.openkilda.testing.service.northbound.model.HaFlowHistoryEntry
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Bandwidth
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.Host
import org.openkilda.testing.service.traffexam.model.TimeLimit
import org.openkilda.testing.service.traffexam.model.Vlan

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableList
import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j

import javax.inject.Provider

/* This class represents any kind of interactions with HA flow
 */
@Slf4j
@EqualsAndHashCode(excludes = 'northboundV2, topologyDefinition')
@Builder
@AutoClone
@ToString(includeNames = true, excludes = 'northboundV2, topologyDefinition')
class HaFlowExtended {
    String haFlowId
    FlowState status
    String statusInfo

    HaFlowSharedEndpoint sharedEndpoint

    long maximumBandwidth
    String pathComputationStrategy
    String encapsulationType
    Long maxLatency
    Long maxLatencyTier2
    boolean ignoreBandwidth
    boolean periodicPings
    boolean pinned
    Integer priority
    boolean strictBandwidth
    String description
    boolean allocateProtectedPath

    Set<String> diverseWithFlows
    Set<String> diverseWithYFlows
    Set<String> diverseWithHaFlows

    List<HaSubFlow> subFlows

    String timeCreate
    String timeUpdate

    @JsonIgnore
    NorthboundServiceV2 northboundV2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    HaFlowExtended(HaFlow haFlow, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition) {
        this.haFlowId = haFlow.haFlowId
        this.status = FlowState.getByValue(haFlow.status)
        this.statusInfo = haFlow.statusInfo
        this.sharedEndpoint = haFlow.sharedEndpoint
        this.maximumBandwidth = haFlow.maximumBandwidth
        this.pathComputationStrategy = haFlow.pathComputationStrategy
        this.encapsulationType = haFlow.encapsulationType
        this.maxLatency = haFlow.maxLatency
        this.maxLatencyTier2 = haFlow.maxLatencyTier2
        this.ignoreBandwidth = haFlow.ignoreBandwidth
        this.periodicPings = haFlow.periodicPings
        this.pinned = haFlow.pinned
        this.priority = haFlow.priority
        this.strictBandwidth = haFlow.strictBandwidth
        this.description = haFlow.description
        this.allocateProtectedPath = haFlow.allocateProtectedPath

        this.diverseWithFlows = haFlow.diverseWithFlows
        this.diverseWithYFlows = haFlow.diverseWithYFlows
        this.diverseWithHaFlows = haFlow.diverseWithHaFlows
        this.subFlows = haFlow.subFlows
        this.timeCreate = haFlow.timeCreate
        this.timeUpdate = haFlow.timeUpdate

        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
    }

    HaFlowAllEntityPaths retrievedAllEntityPaths() {
        HaFlowPaths haFlowPaths = northboundV2.getHaFlowPaths(haFlowId)
        new HaFlowAllEntityPaths(haFlowPaths, topologyDefinition)
    }

    static HaFlowBuilder build(SwitchTriplet swT, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition,
                               boolean useTraffgenPorts = true, List<SwitchPortVlan> busyEndpoints = []) {
        return new HaFlowBuilder(swT, northboundV2, topologyDefinition, useTraffgenPorts, busyEndpoints)
    }

    HaFlowUpdatePayload convertToUpdateRequest() {
        def haFlowCopy = this.clone()
        def builder = HaFlowUpdatePayload.builder()
        HaFlowUpdatePayload.class.getDeclaredFields()*.name.each {
            builder.diverseFlowId(retrieveAnyDiverseFlow())
            if (haFlowCopy.class.declaredFields*.name.contains(it)) {
                builder."$it" = haFlowCopy."$it"
            }
        }
        return builder.build()
    }

    HaFlowHistory getHistory(Long timeFrom = null, Long timeTo = null, Integer maxCount = null) {
        List<HaFlowHistoryEntry> historyRecords =  northboundV2.getHaFlowHistory(haFlowId, timeFrom, timeTo, maxCount)
        return new HaFlowHistory (historyRecords)
    }

    HaFlow waitForBeingInState(FlowState flowState, double timeout = WAIT_OFFSET) {
        def flowDetails
        Wrappers.wait(timeout) {
            flowDetails = northboundV2.getHaFlow(haFlowId)
            assert FlowState.getByValue(flowDetails.status) == flowState && flowDetails.subFlows.every {
                FlowState.getByValue(it.status) == flowState
            }
        }
        flowDetails
    }

    void waitForHistoryEvent(HaFlowActionType action, double timeout = WAIT_OFFSET) {
        Wrappers.wait(timeout) {
            assert getHistory().getEntriesByType(action)[0].payloads.find {it.action == action.payloadLastAction }
        }
    }

    HaFlowExtended partialUpdate(HaFlowPatchPayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'(partial update)")
        northboundV2.partialUpdateHaFlow(haFlowId, updateRequest)
        def haFlow = waitForBeingInState(FlowState.UP)
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    HaFlowExtended update(HaFlowUpdatePayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'")
        northboundV2.updateHaFlow(haFlowId, updateRequest)
        def haFlow = waitForBeingInState(FlowState.UP)
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    HaFlowRerouteResult reroute() {
        log.debug("Reroute ha-flow '${haFlowId}'")
        northboundV2.rerouteHaFlow(haFlowId)
    }

    HaFlowExtended retrieveDetails() {
        log.debug("Getting ha-flow details '${haFlowId}'")
        def haFlow = northboundV2.getHaFlow(haFlowId)
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    HaFlowExtended swap() {
        log.debug("Swap ha-flow '${haFlowId}'")
        def haFlow = northboundV2.swapHaFlowPaths(haFlowId)
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    HaFlowPingResult ping(int timeoutMillis) {
        log.debug("Ping ha-flow '${haFlowId}'")
        northboundV2.pingHaFlow(haFlowId, new HaFlowPingPayload(timeoutMillis))
    }

    List<SwitchPortVlan> occupiedEndpoints() {
        subFlows.collectMany { subFlow ->
            [new SwitchPortVlan(subFlow.endpoint.switchId, subFlow.endpoint.portNumber, subFlow.endpoint.vlanId)]
        } + [new SwitchPortVlan(sharedEndpoint.switchId, sharedEndpoint.portNumber, sharedEndpoint.vlanId)]
    }

    HaFlowValidationResult validate() {
        log.debug("Validate ha-flow '${haFlowId}'")
        northboundV2.validateHaFlow(haFlowId)
    }

    HaFlowSyncResult sync() {
        log.debug("Sync ha-flow '${haFlowId}'")
        northboundV2.syncHaFlow(haFlowId)
    }

    HaFlow delete() {
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert FlowState.getByValue(northboundV2.getHaFlow(haFlowId)?.status) != FlowState.IN_PROGRESS
        }
        log.debug("Deleting ha-flow '$haFlowId'")
        def response = northboundV2.deleteHaFlow(haFlowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !northboundV2.getHaFlow(haFlowId)
        }
        return response
    }

    String retrieveAnyDiverseFlow() {
        if (diverseWithFlows) {
            return diverseWithFlows[0]
        } else if (diverseWithYFlows) {
            return diverseWithYFlows[0]
        } else if (diverseWithHaFlows) {
            return diverseWithHaFlows[0]
        } else {
            return null;
        }
    }

    HaFlowBidirectionalExam traffExam(Provider<TraffExamService> traffExamProvider, long bandwidth = 0, Long duration = 5) {
        log.debug("Traffic generation for ha-flow '${haFlowId}'")
        def traffExam = traffExamProvider.get()
        def subFlow1 = subFlows.first()
        def subFlow2 = subFlows.last()
        Optional<TraffGen> shared = Optional.ofNullable(topologyDefinition.getTraffGen(sharedEndpoint.switchId));
        Optional<TraffGen> ep1 = Optional.ofNullable(topologyDefinition.getTraffGen(subFlow1.endpoint.switchId));
        Optional<TraffGen> ep2 = Optional.ofNullable(topologyDefinition.getTraffGen(subFlow2.endpoint.switchId));
        assert [shared, ep1, ep2].every {it.isPresent()}

        List<Vlan> srcVlanId = ImmutableList.of(new Vlan(sharedEndpoint.vlanId), new Vlan(sharedEndpoint.innerVlanId));
        List<Vlan> dstVlanIds1 = ImmutableList.of(new Vlan(subFlow1.endpoint.vlanId), new Vlan(subFlow1.endpoint.innerVlanId));
        List<Vlan> dstVlanIds2 = ImmutableList.of(new Vlan(subFlow2.endpoint.vlanId), new Vlan(subFlow2.endpoint.innerVlanId));
        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(shared.get().getName());
        Host destHost1 = traffExam.hostByName(ep1.get().getName());
        Host destHost2 = traffExam.hostByName(ep2.get().getName());
        def bandwidthLimit = new Bandwidth(bandwidth)
        if (!bandwidth) {
            bandwidthLimit = new Bandwidth(strictBandwidth && maximumBandwidth ?
                    maximumBandwidth : 0)
        }
        def examBuilder = Exam.builder()
                .flow(null)
                .bandwidthLimit(bandwidthLimit)
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
        Exam forward1 = examBuilder
                .source(sourceHost)
                .sourceVlans(srcVlanId)
                .dest(destHost1)
                .destVlans(dstVlanIds1)
                .build();
        Exam forward2 = examBuilder
                .source(sourceHost)
                .sourceVlans(srcVlanId)
                .dest(destHost2)
                .destVlans(dstVlanIds2)
                .build();
        Exam reverse1 = examBuilder
                .source(destHost1)
                .sourceVlans(dstVlanIds1)
                .dest(sourceHost)
                .destVlans(srcVlanId)
                .build();
        Exam reverse2 = examBuilder
                .source(destHost2)
                .sourceVlans(dstVlanIds2)
                .dest(sourceHost)
                .destVlans(srcVlanId)
                .build();
        return new HaFlowBidirectionalExam(traffExam, forward1, forward2, reverse1, reverse2);
    }
}
