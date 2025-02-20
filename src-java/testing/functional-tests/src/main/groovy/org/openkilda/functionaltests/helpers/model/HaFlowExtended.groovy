package org.openkilda.functionaltests.helpers.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableList
import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.traffic.ha.HaFlowBidirectionalExam
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowStatus
import org.openkilda.model.PathComputationStrategy
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowCreatePayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowUpdatePayload
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
import org.openkilda.testing.tools.SoftAssertions

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_HAFLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

/* This class represents any kind of interactions with HA flow
 */

@Slf4j
@EqualsAndHashCode(excludes = 'northboundV2, topologyDefinition, cleanupManager')
@Builder
@AutoClone
@ToString(includeNames = true, excludes = 'northboundV2, topologyDefinition, cleanupManager')
class HaFlowExtended {
    String haFlowId
    FlowState status
    String statusInfo
    CleanupManager cleanupManager

    HaFlowSharedEndpoint sharedEndpoint

    long maximumBandwidth
    PathComputationStrategy pathComputationStrategy
    FlowEncapsulationType encapsulationType
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

    List<HaSubFlowExtended> subFlows

    String timeCreate
    String timeUpdate

    @JsonIgnore
    NorthboundServiceV2 northboundV2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    HaFlowExtended(String haFlowId,
                   NorthboundServiceV2 northboundV2,
                   TopologyDefinition topologyDefinition,
                   CleanupManager cleanupManager) {
        this.haFlowId = haFlowId
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager
    }

    HaFlowExtended(HaFlow haFlow,
                   NorthboundServiceV2 northboundV2,
                   TopologyDefinition topologyDefinition,
                   CleanupManager cleanupManager) {
        this.haFlowId = haFlow.haFlowId
        this.status = FlowState.getByValue(haFlow.status)
        this.statusInfo = haFlow.statusInfo
        this.sharedEndpoint = haFlow.sharedEndpoint
        this.maximumBandwidth = haFlow.maximumBandwidth
        this.pathComputationStrategy = PathComputationStrategy.valueOf(haFlow.pathComputationStrategy.toUpperCase())
        this.encapsulationType = FlowEncapsulationType.getByValue(haFlow.encapsulationType)
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
        this.subFlows = haFlow.subFlows.collect {
            HaSubFlowExtended.builder()
                    .haSubFlowId(it.flowId)
                    .endpointSwitchId(new SwitchId(it.endpoint.switchId.id))
                    .endpointPort(it.endpoint.portNumber)
                    .endpointVlan(it.endpoint.vlanId)
                    .endpointInnerVlan(it.endpoint.innerVlanId)
                    .description(it.description)
                    .status(FlowStatus.valueOf(it.status.toUpperCase().replace(" ", "_")))
                    .build()
        }
        this.timeCreate = haFlow.timeCreate
        this.timeUpdate = haFlow.timeUpdate

        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager
    }

    HaFlowExtended create(FlowState expectedState = FlowState.UP, CleanupAfter cleanupAfter = TEST) {
        cleanupManager.addAction(DELETE_HAFLOW, { delete() }, cleanupAfter)
        def haFlow = northboundV2.addHaFlow(HaFlowCreatePayload.builder()
                .haFlowId(this.haFlowId)
                .sharedEndpoint(this.sharedEndpoint)
                .maximumBandwidth(this.maximumBandwidth)
                .pathComputationStrategy(this.pathComputationStrategy.toString())
                .encapsulationType(this.encapsulationType.toString())
                .maxLatency(this.maxLatency)
                .maxLatencyTier2(this.maxLatencyTier2)
                .ignoreBandwidth(this.ignoreBandwidth)
                .periodicPings(this.periodicPings)
                .pinned(this.pinned)
                .priority(this.priority)
                .strictBandwidth(this.strictBandwidth)
                .description(this.description)
                .allocateProtectedPath(this.allocateProtectedPath)
        /*A dirty hack to not have 'diverse_flow_id' field in the object (it presents only in create request).
        So we keep one string value added by HaFlowBuilder in 'diverseWithFlows' field, but it's overwritten
        when we create/update HaFlow (based on values in server response which are more interesting)*/
                .diverseFlowId(diverseWithFlows ? diverseWithFlows.first() : null)
                .subFlows(subFlows.collect {
                    HaSubFlowCreatePayload.builder()
                            .endpoint(BaseFlowEndpointV2.builder()
                                    .switchId(it.endpointSwitchId)
                                    .portNumber(it.endpointPort)
                                    .vlanId(it.endpointVlan)
                                    .innerVlanId(it.endpointInnerVlan)
                                    .build())
                            .description(it.description)
                            .build()
                })
                .build())
        return waitForBeingInState(expectedState)
    }

    FlowWithSubFlowsEntityPath retrievedAllEntityPaths() {
        HaFlowPaths haFlowPaths = northboundV2.getHaFlowPaths(haFlowId)
        new FlowWithSubFlowsEntityPath(haFlowPaths, topologyDefinition)
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
        builder.subFlows(subFlows.collect {
            HaSubFlowUpdatePayload.builder()
                    .flowId(it.haSubFlowId)
                    .description(it.description)
                    .endpoint(BaseFlowEndpointV2.builder()
                            .switchId(it.endpointSwitchId)
                            .portNumber(it.endpointPort)
                            .vlanId(it.endpointVlan)
                            .innerVlanId(it.endpointInnerVlan)
                            .build())
                    .build()
        })
        return builder.build()
    }

    HaFlowHistory getHistory(Long timeFrom = null, Long timeTo = null, Integer maxCount = null) {
        List<HaFlowHistoryEntry> historyRecords = northboundV2.getHaFlowHistory(haFlowId, timeFrom, timeTo, maxCount)
        return new HaFlowHistory(historyRecords)
    }

    HaFlowExtended waitForBeingInState(FlowState flowState, double timeout = WAIT_OFFSET) {
        def flowDetails = null
        Wrappers.wait(timeout) {
            flowDetails = northboundV2.getHaFlow(haFlowId)
            assert FlowState.getByValue(flowDetails.status) == flowState && flowDetails.subFlows.every {
                FlowState.getByValue(it.status) == flowState
            }
        }
        return new HaFlowExtended(flowDetails, northboundV2, topologyDefinition, cleanupManager)
    }

    void waitForHistoryEvent(HaFlowActionType action, double timeout = WAIT_OFFSET) {
        Wrappers.wait(timeout) {
            assert getHistory().getEntriesByType(action)[0].payloads.find { it.action == action.payloadLastAction }
        }
    }

    HaFlowExtended sendPartialUpdateRequest(HaFlowPatchPayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'(partial update)")
        def haFlow = northboundV2.partialUpdateHaFlow(haFlowId, updateRequest)
        return new HaFlowExtended(haFlow, northboundV2, topologyDefinition, cleanupManager)
    }

    HaFlowExtended partialUpdate(HaFlowPatchPayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'(partial update)")
        northboundV2.partialUpdateHaFlow(haFlowId, updateRequest)
        return waitForBeingInState(FlowState.UP)
    }

    HaFlowExtended sendUpdateRequest(HaFlowUpdatePayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'")
        def haFlow = northboundV2.updateHaFlow(haFlowId, updateRequest)
        return new HaFlowExtended(haFlow, northboundV2, topologyDefinition, cleanupManager)
    }

    HaFlowExtended update(HaFlowUpdatePayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'")
        northboundV2.updateHaFlow(haFlowId, updateRequest)
        return waitForBeingInState(FlowState.UP)
    }

    HaFlowRerouteResult reroute() {
        log.debug("Reroute ha-flow '${haFlowId}'")
        northboundV2.rerouteHaFlow(haFlowId)
    }

    HaFlowExtended retrieveDetails() {
        log.debug("Getting ha-flow details '${haFlowId}'")
        def haFlow = northboundV2.getHaFlow(haFlowId)
        return new HaFlowExtended(haFlow, northboundV2, topologyDefinition, cleanupManager)
    }

    HaFlowExtended swap() {
        log.debug("Swap ha-flow '${haFlowId}'")
        def haFlow = northboundV2.swapHaFlowPaths(haFlowId)
        return new HaFlowExtended(haFlow, northboundV2, topologyDefinition, cleanupManager)
    }

    HaFlowPingResult ping(HaFlowPingPayload haFlowPingPayload = new HaFlowPingPayload(2000)) {
        log.debug("Ping ha-flow '${haFlowId}'")
        northboundV2.pingHaFlow(haFlowId, haFlowPingPayload)
    }

    ComplexFlowPingResponse pingAndCollectDiscrepancies(HaFlowPingPayload haFlowPingPayload = new HaFlowPingPayload(2000)) {
        def response = ping(haFlowPingPayload)
        assert response.haFlowId == haFlowId, "Ping response for an incorrect ha-flow"
        new ComplexFlowPingResponse(response)
    }

    List<SwitchPortVlan> occupiedEndpoints() {
        subFlows.collectMany { subFlow ->
            [new SwitchPortVlan(subFlow.endpointSwitchId, subFlow.endpointPort, subFlow.endpointVlan)]
        } + [new SwitchPortVlan(sharedEndpoint.switchId, sharedEndpoint.portNumber, sharedEndpoint.vlanId)]
    }

    HaFlowValidationResult validate() {
        log.debug("Validate ha-flow '${haFlowId}'")
        northboundV2.validateHaFlow(haFlowId)
    }

    ComplexFlowValidationResponse validateAndCollectDiscrepancy() {
        def validationResponse = validate()
        new ComplexFlowValidationResponse(validationResponse)
    }

    HaFlowSyncResult sync() {
        log.debug("Sync ha-flow '${haFlowId}'")
        northboundV2.syncHaFlow(haFlowId)
    }

    HaFlow delete() {
        if (haFlowId in northboundV2.getAllHaFlows().haFlowId) {
            Wrappers.wait(WAIT_OFFSET * 2) {
                assert !(FlowState.getByValue(northboundV2.getHaFlow(haFlowId)?.status) in [FlowState.IN_PROGRESS, FlowState.DOWN])
            }
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
            return null
        }
    }

    HaFlowBidirectionalExam traffExam(TraffExamService traffExam, long bandwidth = 0, Long duration = 5) {
        log.debug("Traffic generation for ha-flow '${haFlowId}'")
        def subFlow1 = subFlows.first()
        def subFlow2 = subFlows.last()
        Optional<TraffGen> shared = Optional.ofNullable(topologyDefinition.getTraffGen(sharedEndpoint.switchId, sharedEndpoint.portNumber))
        Optional<TraffGen> ep1 = Optional.ofNullable(topologyDefinition.getTraffGen(subFlow1.endpointSwitchId, subFlow1.endpointPort))
        Optional<TraffGen> ep2 = Optional.ofNullable(topologyDefinition.getTraffGen(subFlow2.endpointSwitchId, subFlow2.endpointPort))
        assert [shared, ep1, ep2].every { it.isPresent() }

        List<Vlan> srcVlanId = ImmutableList.of(new Vlan(sharedEndpoint.vlanId), new Vlan(sharedEndpoint.innerVlanId))
        List<Vlan> dstVlanIds1 = ImmutableList.of(new Vlan(subFlow1.endpointVlan), new Vlan(subFlow1.endpointInnerVlan))
        List<Vlan> dstVlanIds2 = ImmutableList.of(new Vlan(subFlow2.endpointVlan), new Vlan(subFlow2.endpointInnerVlan))
        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(shared.get().getName())
        Host destHost1 = traffExam.hostByName(ep1.get().getName())
        Host destHost2 = traffExam.hostByName(ep2.get().getName())
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
                .build()
        Exam forward2 = examBuilder
                .source(sourceHost)
                .sourceVlans(srcVlanId)
                .dest(destHost2)
                .destVlans(dstVlanIds2)
                .build()
        Exam reverse1 = examBuilder
                .source(destHost1)
                .sourceVlans(dstVlanIds1)
                .dest(sourceHost)
                .destVlans(srcVlanId)
                .build()
        Exam reverse2 = examBuilder
                .source(destHost2)
                .sourceVlans(dstVlanIds2)
                .dest(sourceHost)
                .destVlans(srcVlanId)
                .build()
        return new HaFlowBidirectionalExam(traffExam, forward1, forward2, reverse1, reverse2)
    }

    /**
     * This check allows us to perform main HA-Flow properties comparison after updating operations
     * Note, some fields such as timeUpdate, status, subFlows.timeUpdate, subFlows.status, subFlows.forwardLatency,
     * subFlows.reverseLatency, subFlows.latencyLastModifiedTime are excluded for verification.
     * The main reason for the exclusions above is that these fields are specified by the system automatically.
     * @param expectedHaFlowExtended
     */
    void hasTheSamePropertiesAs(HaFlowExtended expectedHaFlowExtended) {
        SoftAssertions assertions = new SoftAssertions()
        assertions.checkSucceeds { assert this.haFlowId == expectedHaFlowExtended.haFlowId }
        assertions.checkSucceeds { assert this.maximumBandwidth == expectedHaFlowExtended.maximumBandwidth }
        assertions.checkSucceeds { assert this.pathComputationStrategy == expectedHaFlowExtended.pathComputationStrategy }
        assertions.checkSucceeds { assert this.encapsulationType == expectedHaFlowExtended.encapsulationType }
        assertions.checkSucceeds { assert this.maxLatency == expectedHaFlowExtended.maxLatency }
        assertions.checkSucceeds { assert this.maxLatencyTier2 == expectedHaFlowExtended.maxLatencyTier2 }
        assertions.checkSucceeds { assert this.ignoreBandwidth == expectedHaFlowExtended.ignoreBandwidth }
        assertions.checkSucceeds { assert this.periodicPings == expectedHaFlowExtended.periodicPings }
        assertions.checkSucceeds { assert this.pinned == expectedHaFlowExtended.pinned }
        assertions.checkSucceeds { assert this.priority == expectedHaFlowExtended.priority }
        assertions.checkSucceeds { assert this.strictBandwidth == expectedHaFlowExtended.strictBandwidth }
        assertions.checkSucceeds { assert this.description == expectedHaFlowExtended.description }
        assertions.checkSucceeds { assert this.allocateProtectedPath == expectedHaFlowExtended.allocateProtectedPath }
        assertions.checkSucceeds { assert this.diverseWithFlows.sort() == expectedHaFlowExtended.diverseWithFlows.sort() }
        assertions.checkSucceeds { assert this.diverseWithYFlows.sort() == expectedHaFlowExtended.diverseWithYFlows.sort() }
        assertions.checkSucceeds { assert this.diverseWithHaFlows.sort() == expectedHaFlowExtended.diverseWithHaFlows.sort() }

        assertions.checkSucceeds { assert this.sharedEndpoint.switchId == expectedHaFlowExtended.sharedEndpoint.switchId }
        assertions.checkSucceeds { assert this.sharedEndpoint.portNumber == expectedHaFlowExtended.sharedEndpoint.portNumber }
        assertions.checkSucceeds { assert this.sharedEndpoint.vlanId == expectedHaFlowExtended.sharedEndpoint.vlanId }
        assertions.checkSucceeds { assert this.sharedEndpoint.innerVlanId == expectedHaFlowExtended.sharedEndpoint.innerVlanId }


        this.subFlows.each { actualSubFlow ->
            def expectedSubFlow = expectedHaFlowExtended.subFlows.find { it.haSubFlowId== actualSubFlow.haSubFlowId }
            assertions.checkSucceeds { assert actualSubFlow.haSubFlowId == expectedSubFlow.haSubFlowId }

            assertions.checkSucceeds { assert actualSubFlow.endpointSwitchId == expectedSubFlow.endpointSwitchId }
            assertions.checkSucceeds { assert actualSubFlow.endpointPort == expectedSubFlow.endpointPort }
            assertions.checkSucceeds { assert actualSubFlow.endpointVlan == expectedSubFlow.endpointVlan }
            assertions.checkSucceeds { assert actualSubFlow.endpointInnerVlan == expectedSubFlow.endpointInnerVlan }

            assertions.checkSucceeds { assert actualSubFlow.timeCreate == expectedSubFlow.timeCreate }
            assertions.checkSucceeds { assert actualSubFlow.description == expectedSubFlow.description }
        }

        assertions.verify()
    }
}
