package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_FLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowResponsePayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v1.flows.PingOutput
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowStatistics
import org.openkilda.northbound.dto.v2.flows.MirrorPointStatus
import org.openkilda.northbound.dto.v2.flows.PathStatus
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Bandwidth
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam
import org.openkilda.testing.service.traffexam.model.Host
import org.openkilda.testing.service.traffexam.model.TimeLimit
import org.openkilda.testing.service.traffexam.model.Vlan
import org.openkilda.testing.tools.SoftAssertionsWrapper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableList
import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j

@Slf4j
@EqualsAndHashCode(excludes = 'northbound, northboundV2, topologyDefinition')
@Builder
@AutoClone
@ToString(includeNames = true, excludes = 'northbound, northboundV2, topologyDefinition')
class FlowExtended {
    String flowId
    FlowState status
    PathStatus statusDetails
    String statusInfo

    FlowEndpointV2 source
    FlowEndpointV2 destination

    Long maximumBandwidth
    boolean ignoreBandwidth
    boolean strictBandwidth
    boolean periodicPings
    boolean allocateProtectedPath
    boolean pinned
    FlowEncapsulationType encapsulationType
    PathComputationStrategy pathComputationStrategy
    String targetPathComputationStrategy
    Long maxLatency
    Long maxLatencyTier2
    Integer priority
    String description
    String affinityWith
    SwitchId loopSwitchId
    long forwardPathLatencyNs
    long reversePathLatencyNs
    String latencyLastModifiedTime

    String created
    String lastUpdated

    Set<String> diverseWith
    Set<String> diverseWithYFlows
    Set<String> diverseWithHaFlows

    List<MirrorPointStatus> mirrorPointStatus
    String yFlowId
    FlowStatistics statistics

    @JsonIgnore
    NorthboundService northbound

    @JsonIgnore
    NorthboundServiceV2 northboundV2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    @JsonIgnore
    CleanupManager cleanupManager

    FlowExtended(String flowId, NorthboundService northbound, NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition, CleanupManager cleanupManager) {
        this.flowId = flowId
        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager
    }

    FlowExtended(FlowResponseV2 flow, NorthboundService northbound, NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition, CleanupManager cleanupManager) {
        this.flowId = flow.flowId
        this.status = FlowState.getByValue(flow.status)
        this.statusDetails = flow.statusDetails
        this.statusInfo = flow.statusInfo

        this.source = flow.source
        this.destination = flow.destination

        this.maximumBandwidth = flow.maximumBandwidth
        this.ignoreBandwidth  = flow.ignoreBandwidth
        this.strictBandwidth = flow.strictBandwidth
        this.periodicPings = flow.periodicPings
        this.allocateProtectedPath = flow.allocateProtectedPath
        this.pinned = flow.pinned
        this.encapsulationType = FlowEncapsulationType.getByValue(flow.encapsulationType)
        this.pathComputationStrategy = PathComputationStrategy.valueOf(flow.pathComputationStrategy.toUpperCase())
        this.targetPathComputationStrategy = flow.targetPathComputationStrategy
        this.maxLatency = flow.maxLatency
        this.maxLatencyTier2 = flow.maxLatencyTier2
        this.priority = flow.priority
        this.description = flow.description
        this.affinityWith = flow.affinityWith
        this.loopSwitchId = flow.loopSwitchId
        this.forwardPathLatencyNs = flow.forwardPathLatencyNs
        this.reversePathLatencyNs = flow.reversePathLatencyNs
        this.latencyLastModifiedTime = flow.latencyLastModifiedTime

        this.created = flow.created
        this.lastUpdated = flow.lastUpdated

        this.diverseWith = flow.diverseWith
        this.diverseWithYFlows = flow.diverseWithYFlows
        this.diverseWithHaFlows = flow.diverseWithHaFlows

        this.mirrorPointStatus = flow.mirrorPointStatuses
        this.yFlowId = flow.YFlowId
        this.statistics = flow.statistics

        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager
    }

    FlowExtended(FlowPayload flow, NorthboundService northbound, NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition, CleanupManager cleanupManager) {
        this.flowId = flow.id
        this.status = FlowState.getByValue(flow.status)
        this.source = convertEndpointToV2(flow.source)
        this.destination = convertEndpointToV2(flow.destination)
        this.maximumBandwidth = flow.maximumBandwidth
        this.ignoreBandwidth  = flow.ignoreBandwidth
        this.periodicPings = flow.periodicPings
        this.allocateProtectedPath = flow.allocateProtectedPath
        this.pinned = flow.pinned
        this.encapsulationType = FlowEncapsulationType.getByValue(flow.encapsulationType)
        this.pathComputationStrategy = PathComputationStrategy.valueOf(flow.pathComputationStrategy.toUpperCase())
        this.maxLatency = flow.maxLatency
        this.priority = flow.priority
        this.description = flow.description

        this.created = flow.created
        this.lastUpdated = flow.lastUpdated

        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager

        if(flow instanceof FlowResponsePayload) {
            this.diverseWith == flow.diverseWith
            this.statusDetails = !flow.flowStatusDetails ? null : PathStatus.builder()
                    .mainPath(flow.flowStatusDetails.mainFlowPathStatus)
                    .protectedPath(flow.flowStatusDetails.protectedFlowPathStatus).build()
            this.statusInfo = flow.statusInfo
            this.targetPathComputationStrategy = flow.targetPathComputationStrategy
            this.loopSwitchId = flow.loopSwitchId
            this.forwardPathLatencyNs = flow.forwardLatency
            this.reversePathLatencyNs = flow.reverseLatency
            this.latencyLastModifiedTime = flow.latencyLastModifiedTime
            this.yFlowId = flow.getYFlowId()
        }
    }

    FlowExtended create(FlowState expectedState = FlowState.UP, CleanupAfter cleanupAfter = TEST) {
        cleanupManager.addAction(DELETE_FLOW, { delete() }, cleanupAfter)
        sendCreateRequest()
        waitForBeingInState(expectedState)
    }

    FlowExtended sendCreateRequest(CleanupAfter cleanupAfter = TEST) {
        def flowRequest = FlowRequestV2.builder()
                .flowId(flowId)
                .source(source)
                .destination(destination)
                .maximumBandwidth(maximumBandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .strictBandwidth(strictBandwidth)
                .periodicPings(periodicPings)
                .description(description)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .priority(priority)
                .pinned(pinned)
                .allocateProtectedPath(allocateProtectedPath)
                .encapsulationType(encapsulationType ? encapsulationType.toString() : null)
                .pathComputationStrategy(pathComputationStrategy ? pathComputationStrategy.toString() : null)
                .statistics(statistics)
                .affinityFlowId(affinityWith)
                .diverseFlowId(diverseWith ? diverseWith.first() : null)
                .build()
        cleanupManager.addAction(DELETE_FLOW, { delete() }, cleanupAfter)
        def flow = northboundV2.addFlow(flowRequest)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager)
    }

    FlowExtended createV1(FlowState expectedState = FlowState.UP, CleanupAfter cleanupAfter = TEST) {
        def flowRequest = convertToFlowPayload()
        cleanupManager.addAction(DELETE_FLOW, { delete() }, cleanupAfter)
        northbound.addFlow(flowRequest)
        waitForBeingInState(expectedState)
    }

    FlowExtended sendCreateRequestV1(CleanupAfter cleanupAfter = TEST) {
        def flowRequest = convertToFlowPayload()
        cleanupManager.addAction(DELETE_FLOW, { delete() }, cleanupAfter)
        def flow = northbound.addFlow(flowRequest)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager)
    }

    static FlowEndpointV2 convertEndpointToV2(FlowEndpointPayload endpointToConvert) {
        FlowEndpointV2.builder()
                .switchId(endpointToConvert.datapath)
                .portNumber(endpointToConvert.portNumber)
                .vlanId(endpointToConvert.vlanId)
                .innerVlanId(endpointToConvert?.innerVlanId)
                .detectConnectedDevices(new DetectConnectedDevicesV2(
                        endpointToConvert.detectConnectedDevices.lldp,
                        endpointToConvert.detectConnectedDevices.arp))
                .build()
    }

    static FlowEndpointPayload convertEndpointToV1(FlowEndpointV2 endpointToConvert) {
        FlowEndpointPayload.builder()
                .datapath(endpointToConvert.switchId)
                .portNumber(endpointToConvert.portNumber)
                .vlanId(endpointToConvert.vlanId)
                .innerVlanId(endpointToConvert?.innerVlanId)
                .detectConnectedDevices(new DetectConnectedDevicesPayload(false, false))
                .build()
    }

    FlowEntityPath retrieveAllEntityPaths() {
        FlowPathPayload flowPath = northbound.getFlowPath(flowId)
        new FlowEntityPath(flowPath, topologyDefinition)
    }

    def retrieveDetails() {
        log.debug("Get Flow '$flowId' details")
        def flow = northboundV2.getFlow(flowId)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager)
    }

    def retrieveDetailsV1() {
        log.debug("Get Flow '$flowId' details")
        def flow = northbound.getFlow(flowId)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager)
    }

    FlowIdStatusPayload retrieveFlowStatus() {
        log.debug("Get Flow '$flowId' status")
        return northboundV2.getFlowStatus(flowId)
    }

    FlowHistory retrieveFlowHistory() {
        log.debug("Get Flow '$flowId' history details")
        new FlowHistory(northbound.getFlowHistory(flowId))
    }

    List<FlowValidationDto> validate() {
        log.debug("Validate Flow '$flowId'")
        northbound.validateFlow(flowId)
    }

    Map<FlowDirection, PathDiscrepancyDto> validateAndCollectDiscrepancies() {
        def validationResponse = validate()
        assert validationResponse.findAll { !it.asExpected } == validationResponse.findAll { !it.discrepancies.isEmpty() },
                "There is an error in the logic of flow validation"
        validationResponse.findAll { !it.discrepancies.isEmpty() }
                .collectEntries { [(FlowDirection.getByDirection(it.direction)): it.discrepancies] }
    }


    PingOutput ping(PingInput pingInput = new PingInput()) {
        log.debug("Ping Flow '$flowId'")
        northbound.pingFlow(flowId, pingInput)
    }

    FlowExtended update(FlowExtended expectedEntity, FlowState flowState = FlowState.UP) {
        northboundV2.updateFlow(flowId, expectedEntity.convertToUpdate())
        return waitForBeingInState(flowState)
    }

    FlowExtended updateV1(FlowExtended expectedEntity, FlowState flowState = FlowState.UP) {
        northbound.updateFlow(flowId, expectedEntity.convertToFlowPayload())
        return waitForBeingInState(flowState)
    }

    FlowExtended partialUpdate(FlowPatchV2 updateRequest, FlowState flowState = FlowState.UP) {
        northboundV2.partialUpdate(flowId, updateRequest)
        return waitForBeingInState(flowState)
    }

    /*
    This method waits for specific history event about action completion
    Note that the last existing event by action type is checked
    */
    FlowHistoryEventExtension waitForHistoryEvent(FlowActionType actionType, double timeout = WAIT_OFFSET) {
        log.debug("Waiting for Flow '${flowId}' history event $actionType")
        FlowHistoryEventExtension historyEvent = null
        wait(timeout, 1) {
            historyEvent = retrieveFlowHistory().getEntriesByType(actionType).last()
            assert historyEvent.payload.last().action == actionType.payloadLastAction
        }
        return historyEvent
    }

    /*
    This method waits for a flow to be in a desired state.
     */
    FlowExtended waitForBeingInState(FlowState flowState, double timeout = WAIT_OFFSET) {
        log.debug("Waiting for Flow '${flowId}' to be in $flowState")
        FlowExtended flowDetails = null
        wait(timeout) {
            flowDetails = retrieveDetails()
            assert flowDetails.status == flowState
        }
        return flowDetails
    }

    FlowRequestV2 convertToUpdate() {
        def flowCopy = this.clone()
        def builder = FlowRequestV2.builder()
        FlowRequestV2.class.getDeclaredFields()*.name.each {
            builder.diverseFlowId(retrieveAnyDiverseFlow())
            if (flowCopy.class.declaredFields*.name.contains(it)) {
                builder."$it" = flowCopy."$it"
            }
        }
        return builder.build()
    }

    FlowExtended deepCopy() {
        def flowCopy = this.clone()
        flowCopy.setSource(source.jacksonCopy())
        flowCopy.setDestination(destination.jacksonCopy())
        flowCopy.setStatistics(statistics.jacksonCopy())
        return flowCopy
    }

    /**
     * Sends delete request for flow and waits for that flow to disappear from flows list
     */
    FlowResponseV2 delete() {
        if (flowId in northboundV2.getAllFlows()*.getFlowId()) {
            wait(WAIT_OFFSET * 2) { assert retrieveDetails().status != FlowState.IN_PROGRESS }
        }

        log.debug("Deleting flow '$flowId'")
        def response = northboundV2.deleteFlow(flowId)
        wait(FLOW_CRUD_TIMEOUT) {
            assert !retrieveFlowStatus()
            assert retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).first()
                    .payload.last().action == FlowActionType.DELETE.payloadLastAction
        }
        return response
    }

    /**
     * Sends delete request for flow and waits for that flow to disappear from flows list (only V1 API is used)
     */
    FlowPayload deleteV1() {
        if (flowId in northbound.getAllFlows()*.getId()) {
            wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flowId).status != FlowState.IN_PROGRESS }
        }

        log.debug("Deleting flow '$flowId'")
        def response = northbound.deleteFlow(flowId)
        wait(FLOW_CRUD_TIMEOUT) {
            assert !northbound.getFlowStatus(flowId)
            assert retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).first()
                    .payload.last().action == FlowActionType.DELETE.payloadLastAction
        }
        return response
    }

    /**
     * Sends delete request for flow without actual clarification of flow deletion
     */
    FlowResponseV2 sendDeleteRequest() {
        northboundV2.deleteFlow(flowId)
    }

    /**
     * Sends delete request for flow without actual clarification of flow deletion (API.V1)
     */
    FlowPayload sendDeleteRequestV1() {
        northbound.deleteFlow(flowId)
    }

    List<SwitchPortVlan> occupiedEndpoints() {
        [new SwitchPortVlan(source.switchId, source.portNumber, source.vlanId),
         new SwitchPortVlan(destination.switchId, destination.portNumber, destination.vlanId)]
    }

    FlowPayload convertToFlowPayload() {
        FlowPayload.builder()
                .id(flowId)
                .source(convertEndpointToV1(source))
                .destination(convertEndpointToV1(destination))
                .maximumBandwidth(maximumBandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .periodicPings(periodicPings)
                .allocateProtectedPath(allocateProtectedPath)
                .description(description)
                .maxLatency(maxLatency)
                .priority(priority)
                .pinned(pinned)
                .encapsulationType(encapsulationType ? encapsulationType.toString() : null)
                .pathComputationStrategy(pathComputationStrategy ? pathComputationStrategy.toString() : null)
                .build()
    }

    String retrieveAnyDiverseFlow() {
        if (diverseWith) {
            return diverseWith[0]
        } else if (diverseWithYFlows) {
            return diverseWithYFlows[0]
        } else if (diverseWithHaFlows) {
            return diverseWithHaFlows[0]
        } else {
            return null
        }
    }

    /**
     * Build traff exam object for the flow in both directions.
     */
    FlowBidirectionalExam traffExam(TraffExamService traffExam, long bandwidth, Long duration) {

        Optional<TraffGen> srcTg = Optional.ofNullable(topologyDefinition.getTraffGen(source.switchId, source.portNumber))
        Optional<TraffGen> dstTg = Optional.ofNullable(topologyDefinition.getTraffGen(destination.switchId, destination.portNumber))
        assert [srcTg, dstTg].every { it.isPresent() }

        List<Vlan> srcVlanIds = ImmutableList.of(new Vlan(source.vlanId), new Vlan(source.innerVlanId))
        List<Vlan> dstVlanIds = ImmutableList.of(new Vlan(destination.vlanId), new Vlan(destination.innerVlanId))

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(srcTg.get().getName())
        //noinspection ConstantConditions
        Host destHost = traffExam.hostByName(dstTg.get().getName())
        def flow = convertToFlowPayload()
        Exam forward = Exam.builder()
                .flow(flow)
                .source(sourceHost)
                .sourceVlans(srcVlanIds)
                .dest(destHost)
                .destVlans(dstVlanIds)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(0)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build()
        Exam reverse = Exam.builder()
                .flow(flow)
                .source(destHost)
                .sourceVlans(dstVlanIds)
                .dest(sourceHost)
                .destVlans(srcVlanIds)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(0)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build()

        return new FlowBidirectionalExam(forward, reverse)
    }

    void hasTheSamePropertiesAs(FlowExtended expectedFlowExtended) {
        SoftAssertionsWrapper assertions = new SoftAssertionsWrapper()
        assertions.checkSucceeds { assert this.flowId == expectedFlowExtended.flowId }
        assertions.checkSucceeds { assert this.status == expectedFlowExtended.status }
        assertions.checkSucceeds { assert this.statusDetails == expectedFlowExtended.statusDetails }
        assertions.checkSucceeds { assert this.maximumBandwidth == expectedFlowExtended.maximumBandwidth }
        assertions.checkSucceeds { assert this.ignoreBandwidth == expectedFlowExtended.ignoreBandwidth }
        assertions.checkSucceeds { assert this.strictBandwidth == expectedFlowExtended.strictBandwidth }
        assertions.checkSucceeds { assert this.periodicPings == expectedFlowExtended.periodicPings }
        assertions.checkSucceeds { assert this.allocateProtectedPath == expectedFlowExtended.allocateProtectedPath }
        assertions.checkSucceeds { assert this.pinned == expectedFlowExtended.pinned }
        assertions.checkSucceeds { assert this.encapsulationType == expectedFlowExtended.encapsulationType }
        assertions.checkSucceeds { assert this.pathComputationStrategy == expectedFlowExtended.pathComputationStrategy }
        assertions.checkSucceeds { assert this.targetPathComputationStrategy == expectedFlowExtended.targetPathComputationStrategy }
        assertions.checkSucceeds { assert this.maxLatency == expectedFlowExtended.maxLatency }
        assertions.checkSucceeds { assert this.maxLatencyTier2 == expectedFlowExtended.maxLatencyTier2 }
        assertions.checkSucceeds { assert this.priority == expectedFlowExtended.priority }
        assertions.checkSucceeds { assert this.description == expectedFlowExtended.description }
        assertions.checkSucceeds { assert this.affinityWith == expectedFlowExtended.affinityWith }
        assertions.checkSucceeds { assert this.loopSwitchId == expectedFlowExtended.loopSwitchId }
        assertions.checkSucceeds { assert this.diverseWith == expectedFlowExtended.diverseWith }
        assertions.checkSucceeds { assert this.diverseWithYFlows == expectedFlowExtended.diverseWithYFlows }
        assertions.checkSucceeds { assert this.diverseWithHaFlows == expectedFlowExtended.diverseWithHaFlows }
        assertions.checkSucceeds { assert this.mirrorPointStatus == expectedFlowExtended.mirrorPointStatus }
        assertions.checkSucceeds { assert this.yFlowId == expectedFlowExtended.yFlowId}
        assertions.checkSucceeds { assert this.statistics == expectedFlowExtended.statistics }


        assertions.checkSucceeds { assert this.source.switchId == expectedFlowExtended.source.switchId }
        assertions.checkSucceeds { assert this.source.portNumber == expectedFlowExtended.source.portNumber }
        assertions.checkSucceeds { assert this.source.vlanId == expectedFlowExtended.source.vlanId }
        assertions.checkSucceeds { assert this.source.innerVlanId == expectedFlowExtended.source.innerVlanId }
        assertions.checkSucceeds { assert this.source.detectConnectedDevices == expectedFlowExtended.source.detectConnectedDevices }

        assertions.checkSucceeds { assert this.destination.switchId == expectedFlowExtended.destination.switchId }
        assertions.checkSucceeds { assert this.destination.portNumber == expectedFlowExtended.destination.portNumber }
        assertions.checkSucceeds { assert this.destination.vlanId == expectedFlowExtended.destination.vlanId }
        assertions.checkSucceeds { assert this.destination.innerVlanId == expectedFlowExtended.destination.innerVlanId }
        assertions.checkSucceeds { assert this.destination.detectConnectedDevices == expectedFlowExtended.destination.detectConnectedDevices }

        assertions.verify()
    }
}

