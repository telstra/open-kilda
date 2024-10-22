package org.openkilda.functionaltests.helpers.model

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.helpers.FlowNameGenerator.FLOW
import static org.openkilda.functionaltests.helpers.SwitchHelper.randomVlan
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.helpers.model.FlowDirection.*
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_FLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.testing.Constants.EGRESS_RULE_MULTI_TABLE_ID
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.INGRESS_RULE_MULTI_TABLE_ID
import static org.openkilda.testing.Constants.TRANSIT_RULE_MULTI_TABLE_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.info.meter.FlowMeterEntries
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowReroutePayload
import org.openkilda.messaging.payload.flow.FlowResponsePayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowPathDirection
import org.openkilda.model.FlowPathStatus
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v1.flows.PingOutput
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload
import org.openkilda.northbound.dto.v2.flows.FlowLoopResponse
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointsResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowStatistics
import org.openkilda.northbound.dto.v2.flows.MirrorPointStatus
import org.openkilda.northbound.dto.v2.flows.PathStatus
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Bandwidth
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam
import org.openkilda.testing.service.traffexam.model.Host
import org.openkilda.testing.service.traffexam.model.TimeLimit
import org.openkilda.testing.service.traffexam.model.Vlan
import org.openkilda.testing.tools.ConnectedDevice
import org.openkilda.testing.tools.SoftAssertions

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

    List<MirrorPointStatus> mirrorPointStatuses
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

    @JsonIgnore
    Database database

    FlowExtended(String flowId, NorthboundService northbound, NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition, CleanupManager cleanupManager, Database database) {
        this.flowId = flowId
        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager
        this.database = database
    }

    FlowExtended(FlowResponseV2 flow, NorthboundService northbound, NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition, CleanupManager cleanupManager, Database database) {
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

        this.mirrorPointStatuses = flow.mirrorPointStatuses
        this.yFlowId = flow.YFlowId
        this.statistics = flow.statistics

        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
        this.cleanupManager = cleanupManager
        this.database = database
    }

    FlowExtended(FlowPayload flow, NorthboundService northbound, NorthboundServiceV2 northboundV2,
                 TopologyDefinition topologyDefinition, CleanupManager cleanupManager, Database database) {
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
        this.database = database

        if(flow instanceof FlowResponsePayload) {
            this.diverseWith = flow.diverseWith
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
        sendCreateRequest(cleanupAfter)
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
        log.debug("Adding flow '$flowId'")
        def flow = northboundV2.addFlow(flowRequest)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager, database)
    }

    FlowExtended createV1(FlowState expectedState = FlowState.UP, CleanupAfter cleanupAfter = TEST) {
        def flowRequest = convertToFlowCreatePayload()
        cleanupManager.addAction(DELETE_FLOW, { delete() }, cleanupAfter)
        log.debug("Adding flow '$flowId'")
        northbound.addFlow(flowRequest)
        waitForBeingInState(expectedState)
    }

    FlowExtended sendCreateRequestV1(CleanupAfter cleanupAfter = TEST) {
        def flowRequest = convertToFlowPayload()
        cleanupManager.addAction(DELETE_FLOW, { delete() }, cleanupAfter)
        log.debug("Adding flow '$flowId'")
        def flow = northbound.addFlow(flowRequest)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager, database)
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

    FlowMirrorPointPayload buildMirrorPointPayload(SwitchId sinkSwitchId,
                                                   Integer sinkPortNumber,
                                                   Integer sinkVlanId = randomVlan(),
                                                   FlowPathDirection mirrorDirection = FlowPathDirection.FORWARD,
                                                   SwitchId mirrorPointSwitchId = sinkSwitchId) {
        FlowEndpointV2 sinkEndpoint = this.source.switchId == sinkSwitchId ? this.source.jacksonCopy() : this.destination.jacksonCopy()
        sinkEndpoint.tap {
            sinkEndpoint.portNumber = sinkPortNumber
            sinkEndpoint.vlanId = sinkVlanId
            sinkEndpoint.switchId = sinkSwitchId
        }
        FlowMirrorPointPayload.builder()
                .mirrorPointId(FLOW.generateId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(mirrorPointSwitchId)
                .sinkEndpoint(sinkEndpoint)
                .build()
    }

    FlowMirrorPointResponseV2 createMirrorPointWithPayload(FlowMirrorPointPayload mirrorPointPayload,
                                                           boolean withWait=true) {
        def response = northboundV2.createMirrorPoint(flowId, mirrorPointPayload)
        if (withWait) {
            wait(FLOW_CRUD_TIMEOUT) {
                assert retrieveDetails().mirrorPointStatuses[0].status ==
                        FlowPathStatus.ACTIVE.toString().toLowerCase()
                assert !retrieveFlowHistory().getEntriesByType(FlowActionType.CREATE_MIRROR).isEmpty()
            }
        }
        return response
    }

    FlowMirrorPointResponseV2 createMirrorPoint(SwitchId sinkSwitchId,
                                                Integer sinkPortNumber,
                                                Integer sinkVlanId = randomVlan(),
                                                FlowPathDirection mirrorDirection = FlowPathDirection.FORWARD,
                                                SwitchId mirrorPointSwitchId = sinkSwitchId,
                                                boolean withWait=true) {
        FlowMirrorPointPayload mirrorPointPayload = buildMirrorPointPayload(
                sinkSwitchId, sinkPortNumber, sinkVlanId, mirrorDirection, mirrorPointSwitchId)
        return createMirrorPointWithPayload(mirrorPointPayload, withWait)
    }

    FlowEntityPath retrieveAllEntityPaths() {
        log.debug("Getting Flow path for '$flowId'")
        FlowPathPayload flowPath = northbound.getFlowPath(flowId)
        new FlowEntityPath(flowPath, topologyDefinition)
    }

    def retrieveDetails() {
        log.debug("Getting Flow '$flowId' details")
        def flow = northboundV2.getFlow(flowId)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager, database)
    }

    def retrieveDetailsFromDB() {
        log.debug("Getting DB details for Flow '$flowId'")
        database.getFlow(flowId)
    }

    def retrieveDetailsV1() {
        log.debug("Getting Flow '$flowId' details")
        def flow = northbound.getFlow(flowId)
        return new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager, database)
    }

    FlowIdStatusPayload retrieveFlowStatus() {
        log.debug("Getting Flow '$flowId' status")
        return northboundV2.getFlowStatus(flowId)
    }

    FlowHistory retrieveFlowHistory(Long timeFrom = null , Long timeTo = null) {
        log.debug("Getting Flow '$flowId' history details")
        new FlowHistory(northbound.getFlowHistory(flowId, timeFrom, timeTo))
    }

    int retrieveHistoryEventsNumber() {
        retrieveFlowHistory().getEventsNumber()
    }

    List<FlowHistoryStatus> retrieveFlowHistoryStatus(Long timeFrom = null, Long timeTo = null, Integer maxCount = null) {
        log.debug("Getting '$flowId' Flow history status")
        northboundV2.getFlowHistoryStatuses(flowId, timeFrom, timeTo, maxCount).historyStatuses.collect {
            new FlowHistoryStatus(it.timestamp, it.statusBecome)
        }
    }

    List<FlowHistoryStatus> retrieveFlowHistoryStatus(Integer maxCount) {
        log.debug("Getting '$flowId' Flow history status")
        retrieveFlowHistoryStatus(null, null, maxCount)
    }

    FlowMirrorPointsResponseV2 retrieveMirrorPoints() {
        log.debug("Getting Flow '$flowId' mirror points")
        return northboundV2.getMirrorPoints(flowId)
    }

    FlowMirrorPointResponseV2 deleteMirrorPoint(String mirrorPointId) {
        log.debug("Deleting mirror point '$mirrorPointId' of the flow '$flowId'")
        northboundV2.deleteMirrorPoint(flowId, mirrorPointId)
    }

    List<FlowValidationDto> validate() {
        log.debug("Validating Flow '$flowId'")
        northbound.validateFlow(flowId)
    }

    FlowRerouteResponseV2 reroute() {
        log.debug("Rerouting Flow '$flowId'")
        northboundV2.rerouteFlow(flowId)
    }

    Map<FlowDirection, PathDiscrepancyDto> validateAndCollectDiscrepancies() {
        def validationResponse = validate()
        assert validationResponse.findAll { !it.asExpected } == validationResponse.findAll { !it.discrepancies.isEmpty() },
                "There is an error in the logic of flow validation"
        validationResponse.findAll { !it.discrepancies.isEmpty() }
                .collectEntries { [(getByDirection(it.direction)): it.discrepancies] }
    }

    Map<FlowDirection, String> pingAndCollectDiscrepancies(PingInput pingInput = new PingInput()) {
        def pingResponse = ping(pingInput)
        assert pingResponse.flowId == flowId, "Ping response for an incorrect flow"
        verifyPingLogic(pingResponse, FORWARD)
        verifyPingLogic(pingResponse, REVERSE)

        Map<FlowDirection, String> discrepancies = [:]
        pingResponse.forward.pingSuccess ?: discrepancies.put(FORWARD, pingResponse.forward.error)
        pingResponse.reverse.pingSuccess ?: discrepancies.put(REVERSE, pingResponse.reverse.error)
        return discrepancies
    }

    static private void verifyPingLogic(PingOutput pingPayload, FlowDirection direction) {
        def pingResult = direction == FORWARD ? pingPayload.forward : pingPayload.reverse
        assert (pingResult.pingSuccess && !pingResult.error) || (!pingResult.pingSuccess && pingResult.error),
                "There is an error in the ping logic for $pingResult"
    }

    FlowReroutePayload sync() {
        log.debug("Sync Flow '$flowId'")
        northbound.synchronizeFlow(flowId)
    }

    PingOutput ping(PingInput pingInput = new PingInput()) {
        log.debug("Ping Flow '$flowId'")
        northbound.pingFlow(flowId, pingInput)
    }

    FlowExtended sendUpdateRequest(FlowExtended expectedEntity) {
        log.debug("Updating Flow '$flowId'")
        def response = northboundV2.updateFlow(flowId, expectedEntity.convertToUpdate())
        return new FlowExtended(response, northbound, northboundV2, topologyDefinition, cleanupManager, database)
    }

    FlowExtended update(FlowExtended expectedEntity, FlowState flowState = FlowState.UP) {
        sendUpdateRequest(expectedEntity)
        return waitForBeingInState(flowState)
    }

    FlowExtended updateV1(FlowExtended expectedEntity, FlowState flowState = FlowState.UP) {
        log.debug("Updating(V1) Flow '$flowId'")
        northbound.updateFlow(flowId, expectedEntity.convertToFlowPayload())
        return waitForBeingInState(flowState)
    }

    FlowExtended partialUpdate(FlowPatchV2 updateRequest, FlowState flowState = FlowState.UP) {
        sendPartialUpdateRequest(updateRequest)
        return waitForBeingInState(flowState)
    }

    FlowReroutePayload rerouteV1() {
        log.debug("Rerouting(V1) Flow '$flowId'")
        return northbound.rerouteFlow(flowId)
    }

    FlowExtended sendPartialUpdateRequest(FlowPatchV2 updateRequest) {
        log.debug("Partitial updating Flow '$flowId'")
        def response = northboundV2.partialUpdate(flowId, updateRequest)
        return new FlowExtended(response, northbound, northboundV2, topologyDefinition, cleanupManager, database)

    }

    FlowExtended partialUpdateV1(FlowPatchDto updateRequest, FlowState flowState = FlowState.UP) {
        sendPartialUpdateRequestV1(updateRequest)
        return waitForBeingInState(flowState)
    }

    FlowExtended sendPartialUpdateRequestV1(FlowPatchDto updateRequest) {
        log.debug("Partitial updating(V1) Flow '$flowId'")
        def response = northbound.partialUpdate(flowId, updateRequest)
        return new FlowExtended(response, northbound, northboundV2, topologyDefinition, cleanupManager, database)

    }

    void updateFlowBandwidthInDB(long newBandwidth) {
        log.debug("Updating Flow '$flowId' bandwidth in DB")
        database.updateFlowBandwidth(flowId, newBandwidth)
    }

    void updateFlowMeterIdInDB(long newMeterId) {
        log.debug("Updating Flow '$flowId' meter in DB")
        database.updateFlowMeterId(flowId, newMeterId)
    }

    boolean isFlowAtSingleSwitch() {
        return source.switchId == destination.switchId
    }

    def getFlowRulesCountBySwitch(FlowDirection direction, int involvedSwitchesCount, boolean isSwitchServer42) {
        def flowEndpoint = direction == FORWARD ? source : destination
        def swProps = northbound.getSwitchProperties(flowEndpoint.switchId)
        int count = involvedSwitchesCount - 1;

        count += 1 // customer input rule
        count += (flowEndpoint.vlanId != 0) ? 1 : 0 // pre ingress rule
        count += 1 // multi table ingress rule

        def server42 = isSwitchServer42 && !isFlowAtSingleSwitch()
        if (server42) {
            count += (flowEndpoint.vlanId != 0) ? 1 : 0 // shared server42 rule
            count += 2 // ingress server42 rule and server42 input rule
        }

        count += (swProps.switchLldp || flowEndpoint.detectConnectedDevices.lldp) ? 1 : 0 // lldp rule
        count += (swProps.switchArp || flowEndpoint.detectConnectedDevices.arp) ? 1 : 0 // arp rule
        return count
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

    /*
    This method swaps the main path with the protected path
     */
    FlowExtended swapFlowPath() {
        log.debug("Swapping Flow '$flowId' path")
        def flow = northbound.swapFlowPath(flowId)
        new FlowExtended(flow, northbound, northboundV2, topologyDefinition, cleanupManager, database)
    }

    FlowLoopResponse createFlowLoop(SwitchId switchId) {
        log.debug("Creating loop Flow '$flowId'")
        northboundV2.createFlowLoop(flowId, new FlowLoopPayload(switchId))
    }

    List<FlowLoopResponse> retrieveFlowLoop(SwitchId switchId = null) {
        log.debug("Getting loop Flow '$flowId'")
        northboundV2.getFlowLoop(flowId, switchId)
    }

    FlowLoopResponse deleteFlowLoop() {
        log.debug("Deleting loop Flow '$flowId'")
        northboundV2.deleteFlowLoop(flowId)
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

    FlowMeterEntries resetMeters() {
        log.debug("Resetting meters Flow '$flowId'")
        northbound.resetMeters(flowId)
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
            assert retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).last()
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

        log.debug("Deleting Flow '$flowId'")
        def response = northbound.deleteFlow(flowId)
        wait(FLOW_CRUD_TIMEOUT) {
            assert !northbound.getFlowStatus(flowId)
            assert retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).last()
                    .payload.last().action == FlowActionType.DELETE.payloadLastAction
        }
        return response
    }

    /**
     * Sends delete request for flow without actual clarification of flow deletion
     */
    FlowResponseV2 sendDeleteRequest() {
        log.debug("Deleting Flow '$flowId'")
        northboundV2.deleteFlow(flowId)
    }

    /**
     * Sends delete request for flow without actual clarification of flow deletion (API.V1)
     */
    FlowPayload sendDeleteRequestV1() {
        log.debug("Deleting(V1) Flow '$flowId'")
        northbound.deleteFlow(flowId)
    }

    /**
     * This method gets info about connected devices to flow src and dst switches
     * @param since can be used to get details about connected devices starting from a specific time
     */
    FlowConnectedDevicesResponse retrieveConnectedDevices(String since = null) {
        log.debug("Getting connected devices Flow '$flowId'")
        northbound.getFlowConnectedDevices(flowId, since)
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

    FlowCreatePayload convertToFlowCreatePayload() {
        new FlowCreatePayload(
                flowId, convertEndpointToV1(source), convertEndpointToV1(destination), maximumBandwidth, ignoreBandwidth,
                periodicPings, allocateProtectedPath, description, null, null,
                (diverseWith ? diverseWith.first() : null), null, maxLatency, priority, pinned,
                (encapsulationType ? encapsulationType.toString() : null),
                (pathComputationStrategy ? pathComputationStrategy.toString() : null)
        )
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

    ConnectedDevice sourceConnectedDeviceExam(TraffExamService tgService, List<Integer> vlanIds = null) {
        vlanIds ? buildConnectedDeviceExam(tgService, this.source, vlanIds) : buildConnectedDeviceExam(tgService, this.source)
    }

    ConnectedDevice destinationConnectedDeviceExam(TraffExamService tgService, List<Integer> vlanIds = null) {
        vlanIds ? buildConnectedDeviceExam(tgService, this.destination, vlanIds) : buildConnectedDeviceExam(tgService, this.destination)
    }

    ConnectedDevice buildConnectedDeviceExam(TraffExamService tgService, FlowEndpointV2 endpoint,
                                             List<Integer> vlanIds = [endpoint?.vlanId, endpoint?.innerVlanId]) {
        Optional<TraffGen> tg = Optional.ofNullable(topologyDefinition.getTraffGen(endpoint.switchId, endpoint.portNumber))
        cleanupManager.addAction(OTHER, { database.removeConnectedDevices(tg.get().getSwitchConnected().dpId) })
        def device = new ConnectedDevice(tgService, tg.get(), vlanIds)
        cleanupManager.addAction(OTHER, { device.close() })
        return device
    }

    void hasTheSamePropertiesAs(FlowExtended expectedFlowExtended) {
        SoftAssertions assertions = new SoftAssertions()
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
        assertions.checkSucceeds { assert this.mirrorPointStatuses == expectedFlowExtended.mirrorPointStatuses }
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

    void hasTheSameDetectedDevicesAs(FlowExtended expectedFlowExtended) {
        SoftAssertions assertions = new SoftAssertions()

        assertions.checkSucceeds { assert this.source.detectConnectedDevices.lldp == expectedFlowExtended.source.detectConnectedDevices.lldp }
        assertions.checkSucceeds { assert this.source.detectConnectedDevices.arp == expectedFlowExtended.source.detectConnectedDevices.arp }

        assertions.checkSucceeds { assert this.destination.detectConnectedDevices.lldp == expectedFlowExtended.destination.detectConnectedDevices.lldp }
        assertions.checkSucceeds { assert this.destination.detectConnectedDevices.arp == expectedFlowExtended.destination.detectConnectedDevices.arp }

        assertions.verify()
    }

    /**
     * Check that all needed rules are created for a flow with protected path.<br>
     * Protected path creates the 'egress' rule only on the src and dst switches
     * and creates 2 rules(input/output) on the transit switches.<br>
     * if (switchId == src/dst): 2 rules for main flow path + 1 egress for protected path = 3<br>
     * if (switchId != src/dst): 2 rules for main flow path + 2 rules for protected path = 4<br>
     *
     * @param flowInvolvedSwitchesWithRules (map of switch-rules data for further verification)
     */
    void verifyRulesForProtectedFlowOnSwitches(HashMap<SwitchId, List<FlowEntry>> flowInvolvedSwitchesWithRules) {
        def flowDBInfo = retrieveDetailsFromDB()
        long mainForwardCookie = flowDBInfo.forwardPath.cookie.value
        long mainReverseCookie = flowDBInfo.reversePath.cookie.value
        long protectedForwardCookie = flowDBInfo.protectedForwardPath.cookie.value
        long protectedReverseCookie = flowDBInfo.protectedReversePath.cookie.value
        assert protectedForwardCookie && protectedReverseCookie, "Flow doesn't have protected path(no protected path cookies in DB)"

        def rulesOnSrcSwitch = flowInvolvedSwitchesWithRules.get(this.source.switchId)
        assert rulesOnSrcSwitch.find { it.cookie == mainForwardCookie }.tableId == INGRESS_RULE_MULTI_TABLE_ID
        assert rulesOnSrcSwitch.find { it.cookie == mainReverseCookie }.tableId == EGRESS_RULE_MULTI_TABLE_ID
        assert rulesOnSrcSwitch.find { it.cookie == protectedReverseCookie }.tableId == EGRESS_RULE_MULTI_TABLE_ID
        assert !rulesOnSrcSwitch*.cookie.contains(protectedForwardCookie)

        def rulesOnDstSwitch = flowInvolvedSwitchesWithRules.get(this.destination.switchId)
        assert rulesOnDstSwitch.find { it.cookie == mainForwardCookie }.tableId == EGRESS_RULE_MULTI_TABLE_ID
        assert rulesOnDstSwitch.find { it.cookie == mainReverseCookie }.tableId == INGRESS_RULE_MULTI_TABLE_ID
        assert rulesOnDstSwitch.find { it.cookie == protectedForwardCookie }.tableId == EGRESS_RULE_MULTI_TABLE_ID
        assert !rulesOnDstSwitch*.cookie.contains(protectedReverseCookie)

        def flowPathInfo = retrieveAllEntityPaths()
        List<SwitchId> mainFlowSwitches = flowPathInfo.flowPath.path.forward.getInvolvedSwitches()
        List<SwitchId> mainFlowTransitSwitches = flowPathInfo.flowPath.path.forward.getTransitInvolvedSwitches()

        List<SwitchId> protectedFlowSwitches = flowPathInfo.flowPath.protectedPath.forward.getInvolvedSwitches()
        List<SwitchId> protectedFlowTransitSwitches = flowPathInfo.flowPath.protectedPath.forward.getTransitInvolvedSwitches()

        def commonSwitches = mainFlowSwitches.intersect(protectedFlowSwitches)
        def commonTransitSwitches = mainFlowTransitSwitches.intersect(protectedFlowTransitSwitches)

        def uniqueTransitSwitchesMainPath = mainFlowTransitSwitches.findAll { !commonSwitches.contains(it) }
        def uniqueTransitSwitchesProtectedPath = protectedFlowTransitSwitches.findAll { !commonSwitches.contains(it) }

        def transitTableId = TRANSIT_RULE_MULTI_TABLE_ID
        withPool {
            flowInvolvedSwitchesWithRules.findAll { it.getKey() in commonTransitSwitches }.each { rulesPerSwitch ->
                assert rulesPerSwitch.getValue().find { it.cookie == mainForwardCookie }?.tableId == transitTableId
                assert rulesPerSwitch.getValue().find { it.cookie == mainReverseCookie }?.tableId == transitTableId
                assert rulesPerSwitch.getValue().find { it.cookie == protectedForwardCookie }?.tableId == transitTableId
                assert rulesPerSwitch.getValue().find { it.cookie == protectedReverseCookie }?.tableId == transitTableId
            }
        }
        //this loop checks rules on unique transit nodes
        withPool {
            flowInvolvedSwitchesWithRules.findAll { it.getKey() in uniqueTransitSwitchesProtectedPath }
                    .each { rulesPerSwitch ->
                        assert rulesPerSwitch.getValue().find { it.cookie == protectedForwardCookie }?.tableId == transitTableId
                        assert rulesPerSwitch.getValue().find { it.cookie == protectedReverseCookie }?.tableId == transitTableId
                    }
        }
        //this loop checks rules on unique main nodes
        withPool {
            flowInvolvedSwitchesWithRules.findAll { it.getKey() in uniqueTransitSwitchesMainPath }.each { rulesPerSwitch ->
                assert rulesPerSwitch.getValue().find { it.cookie == mainForwardCookie }?.tableId == transitTableId
                assert rulesPerSwitch.getValue().find { it.cookie == mainReverseCookie }?.tableId == transitTableId
            }
        }
    }
}

