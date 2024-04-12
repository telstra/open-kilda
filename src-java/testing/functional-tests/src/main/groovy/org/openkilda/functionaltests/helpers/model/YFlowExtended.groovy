package org.openkilda.functionaltests.helpers.model


import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.yflows.SubFlow
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowSyncResult
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Bandwidth
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.Host
import org.openkilda.testing.service.traffexam.model.TimeLimit
import org.openkilda.testing.service.traffexam.model.Vlan
import org.openkilda.testing.service.traffexam.model.YFlowBidirectionalExam
import org.openkilda.testing.tools.SoftAssertionsWrapper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
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
class YFlowExtended {

    String yFlowId
    FlowState status

    YFlowSharedEndpoint sharedEndpoint

    long maximumBandwidth
    String pathComputationStrategy
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

    @JsonProperty("y_point")
    SwitchId yPoint
    @JsonProperty("protected_path_y_point")
    SwitchId protectedPathYPoint

    Set<String> diverseWithFlows
    @JsonProperty("diverse_with_y_flows")
    Set<String> diverseWithYFlows
    Set<String> diverseWithHaFlows

    List<SubFlow> subFlows

    String timeCreate
    String timeUpdate

    @JsonIgnore
    NorthboundService northbound

    @JsonIgnore
    NorthboundServiceV2 northboundV2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    YFlowExtended(YFlow yFlow, NorthboundService northbound, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition) {
        this.yFlowId = yFlow.YFlowId
        this.status = FlowState.getByValue(yFlow.status)

        this.maximumBandwidth = yFlow.maximumBandwidth
        this.pathComputationStrategy = yFlow.pathComputationStrategy
        this.encapsulationType = FlowEncapsulationType.getByValue(yFlow.encapsulationType)
        this.maxLatency = yFlow.maxLatency
        this.maxLatencyTier2 = yFlow.maxLatencyTier2
        this.ignoreBandwidth = yFlow.ignoreBandwidth
        this.periodicPings = yFlow.periodicPings
        this.pinned = yFlow.pinned
        this.priority = yFlow.priority
        this.strictBandwidth = yFlow.strictBandwidth
        this.description = yFlow.description
        this.allocateProtectedPath = yFlow.allocateProtectedPath

        this.yPoint = yFlow.YPoint
        this.protectedPathYPoint = yFlow.protectedPathYPoint

        this.sharedEndpoint = yFlow.sharedEndpoint
        this.subFlows = yFlow.subFlows

        this.diverseWithFlows = yFlow.diverseWithFlows
        this.diverseWithYFlows = yFlow.diverseWithYFlows
        this.diverseWithHaFlows = yFlow.diverseWithHaFlows

        this.timeCreate = yFlow.timeCreate
        this.timeUpdate = yFlow.timeUpdate

        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
    }

    FlowWithSubFlowsEntityPath retrieveAllEntityPaths() {
        YFlowPaths yFlowPaths = northboundV2.getYFlowPaths(yFlowId)
        new FlowWithSubFlowsEntityPath(yFlowPaths, topologyDefinition)
    }

    /*
    This method waits for a flow to be in a desired state.
    As Y-Flow(main flow) is a complex flow that contains two regular flows(sub-flows), its state depends on these regular flows.
    In the case of UP or DOWN state, all three flows should be in the same state.
    As for the IN_PROGRESS and DEGRADED states, only the Y-Flow state is checked.
    Verification of sub-flow states should be in the scope of the main test.
     */

    YFlowExtended waitForBeingInState(FlowState flowState, double timeout = WAIT_OFFSET) {
        log.debug("Waiting for Y-Flow '${yFlowId}' to be in $flowState")
        YFlowExtended flowDetails = null
        Wrappers.wait(timeout) {
            flowDetails = retrieveDetails()
            if (flowState in [FlowState.UP, FlowState.DOWN]) {
                assert flowDetails.status == flowState && flowDetails.subFlows.every {
                    FlowState.getByValue(it.status) == flowState
                }
            } else {
                assert flowDetails.status == flowState
            }
        }
        flowDetails
    }

    YFlowExtended sendPartialUpdateRequest(YFlowPatchPayload updateRequest) {
        log.debug("Send update request Y-Flow '${yFlowId}'(partial update)")
        def yFlow = northboundV2.partialUpdateYFlow(yFlowId, updateRequest)
        new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition)
    }

    YFlowExtended partialUpdate(YFlowPatchPayload updateRequest, FlowState flowState = FlowState.UP) {
        log.debug("Update Y-Flow '${yFlowId}'(partial update)")
        northboundV2.partialUpdateYFlow(yFlowId, updateRequest)
        waitForBeingInState(flowState)
    }

    YFlowExtended sendUpdateRequest(YFlowUpdatePayload updateRequest) {
        log.debug("Send update request Y-Flow '${yFlowId}'")
        def yFlow = northboundV2.updateYFlow(yFlowId, updateRequest)
        new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition)
    }

    YFlowExtended update(YFlowUpdatePayload updateRequest, FlowState flowState = FlowState.UP) {
        log.debug("Update Y-Flow '${yFlowId}'")
        northboundV2.updateYFlow(yFlowId, updateRequest)
        waitForBeingInState(flowState)
    }

    YFlowExtended retrieveDetails() {
        log.debug("Get Y-Flow '${yFlowId}' details")
        def yFlow = northboundV2.getYFlow(yFlowId)
        return  yFlow ? new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition) : null
    }

    YFlowExtended swap() {
        log.debug("Swap Y-Flow '${yFlowId}'")
        def yFlow = northboundV2.swapYFlowPaths(yFlowId)
        new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition)
    }

    YFlowValidationResult validate() {
        log.debug("Validate Y-Flow '${yFlowId}'")
        northboundV2.validateYFlow(yFlowId)
    }

    YFlowSyncResult sync() {
        log.debug("Synchronize Y-Flow '${yFlowId}'")
        northboundV2.synchronizeYFlow(yFlowId)
    }

    FlowHistory retrieveFlowHistory() {
        new FlowHistory(northbound.getFlowHistory(yFlowId))
    }

    YFlowPingResult ping(YFlowPingPayload payload = new YFlowPingPayload(2000)) {
        northboundV2.pingYFlow(yFlowId, payload)
    }

    YFlowRerouteResult reroute() {
        return northboundV2.rerouteYFlow(yFlowId)
    }

    void waitForHistoryEvent(YFlowActionType action, double timeout = WAIT_OFFSET) {
        Wrappers.wait(timeout) {
            assert retrieveFlowHistory().getEntriesByType(action)[0].payload.find { it.action == action.payloadLastAction }
        }
    }

    FlowHistory retrieveSubFlowHistory(String subFlowId) {
       new FlowHistory(northbound.getFlowHistory(subFlowId))
    }

    List<SwitchPortVlan> occupiedEndpoints() {
        subFlows.collectMany { subFlow ->
            [new SwitchPortVlan(subFlow.endpoint.switchId, subFlow.endpoint.portNumber, subFlow.endpoint.vlanId),
             new SwitchPortVlan(sharedEndpoint.switchId, sharedEndpoint.portNumber, subFlow.sharedEndpoint.vlanId)]
        }
    }

    YFlowUpdatePayload convertToUpdate() {
        def yFlowCopy = this.clone()
        def builder = YFlowUpdatePayload.builder()
        YFlowUpdatePayload.class.getDeclaredFields()*.name.each {
            builder.diverseFlowId(retrieveAnyDiverseFlow())
            if (yFlowCopy.class.declaredFields*.name.contains(it)) {
                builder."$it" = yFlowCopy."$it"
            }
        }
        return builder.build()
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

    /**
     * Deleting Y-Flow and waits when the flow disappears from the flow list.
     */
    YFlow delete() {
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert retrieveDetails()?.status != FlowState.IN_PROGRESS
        }
        log.debug("Deleting Y-Flow '$yFlowId'")
        def response = northboundV2.deleteYFlow(yFlowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !retrieveDetails()
            assert retrieveFlowHistory().getEntriesByType(YFlowActionType.DELETE).first()
                    .payload.last().action == YFlowActionType.DELETE.payloadLastAction
        }
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(response.sharedEndpoint.switchId, true)
        return response
    }

    /**
     * Build traff exam object for both 'y-flow' subflows in both directions.
     */
    YFlowBidirectionalExam traffExam(TraffExamService traffExam, long bandwidth, Long duration) {
        def subFlow1 = subFlows.first()
        def subFlow2 = subFlows.last()
        Optional<TraffGen> source = Optional.ofNullable(topologyDefinition.getTraffGen(sharedEndpoint.switchId, sharedEndpoint.portNumber))
        Optional<TraffGen> dest1 = Optional.ofNullable(topologyDefinition.getTraffGen(subFlow1.endpoint.switchId, subFlow1.endpoint.portNumber))
        Optional<TraffGen> dest2 = Optional.ofNullable(topologyDefinition.getTraffGen(subFlow2.endpoint.switchId, subFlow2.endpoint.portNumber))
        assert [source, dest1, dest2].every { it.isPresent() }

        List<Vlan> srcVlanIds1 = ImmutableList.of(new Vlan(subFlow1.getSharedEndpoint().getVlanId()),
                new Vlan(subFlow1.getSharedEndpoint().getInnerVlanId()))
        List<Vlan> srcVlanIds2 = ImmutableList.of(new Vlan(subFlow2.getSharedEndpoint().getVlanId()),
                new Vlan(subFlow2.getSharedEndpoint().getInnerVlanId()))
        List<Vlan> dstVlanIds1 = ImmutableList.of(new Vlan(subFlow1.getEndpoint().getVlanId()),
                new Vlan(subFlow1.getEndpoint().getInnerVlanId()));
        List<Vlan> dstVlanIds2 = ImmutableList.of(new Vlan(subFlow2.getEndpoint().getVlanId()),
                new Vlan(subFlow2.getEndpoint().getInnerVlanId()))

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(source.get().getName())
        //noinspection ConstantConditions
        Host destHost1 = traffExam.hostByName(dest1.get().getName())
        Host destHost2 = traffExam.hostByName(dest2.get().getName())

        Exam forward1 = Exam.builder()
                .flow(null)
                .source(sourceHost)
                .sourceVlans(srcVlanIds1)
                .dest(destHost1)
                .destVlans(dstVlanIds1)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build()
        Exam forward2 = Exam.builder()
                .flow(null)
                .source(sourceHost)
                .sourceVlans(srcVlanIds2)
                .dest(destHost2)
                .destVlans(dstVlanIds2)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build()
        Exam reverse1 = Exam.builder()
                .flow(null)
                .source(destHost1)
                .sourceVlans(dstVlanIds1)
                .dest(sourceHost)
                .destVlans(srcVlanIds1)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build()
        Exam reverse2 = Exam.builder()
                .flow(null)
                .source(destHost2)
                .sourceVlans(dstVlanIds2)
                .dest(sourceHost)
                .destVlans(srcVlanIds2)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build()

        return new YFlowBidirectionalExam(forward1, reverse1, forward2, reverse2)
    }

    /**
     * This check allows us to perform main Y-Flow properties comparison after updating operations
     * Note, some fields such as subFlows.timeUpdate, subFlows.status, timeUpdate, and status are excluded for verification.
     * @param expectedYFlowExtended
     * @param isYPointVerificationIncluded
     */
    void hasTheSamePropertiesAs(YFlowExtended expectedYFlowExtended, boolean isYPointVerificationIncluded = true) {
        SoftAssertionsWrapper assertions = new SoftAssertionsWrapper()
        assertions.checkSucceeds { assert this.yFlowId == expectedYFlowExtended.yFlowId }
        assertions.checkSucceeds { assert this.maximumBandwidth == expectedYFlowExtended.maximumBandwidth }
        assertions.checkSucceeds { assert this.pathComputationStrategy == expectedYFlowExtended.pathComputationStrategy }
        assertions.checkSucceeds { assert this.encapsulationType == expectedYFlowExtended.encapsulationType }
        assertions.checkSucceeds { assert this.maxLatency == expectedYFlowExtended.maxLatency }
        assertions.checkSucceeds { assert this.maxLatencyTier2 == expectedYFlowExtended.maxLatencyTier2 }
        assertions.checkSucceeds { assert this.ignoreBandwidth == expectedYFlowExtended.ignoreBandwidth }
        assertions.checkSucceeds { assert this.periodicPings == expectedYFlowExtended.periodicPings }
        assertions.checkSucceeds { assert this.pinned == expectedYFlowExtended.pinned }
        assertions.checkSucceeds { assert this.priority == expectedYFlowExtended.priority }
        assertions.checkSucceeds { assert this.strictBandwidth == expectedYFlowExtended.strictBandwidth }
        assertions.checkSucceeds { assert this.description == expectedYFlowExtended.description }
        assertions.checkSucceeds { assert this.allocateProtectedPath == expectedYFlowExtended.allocateProtectedPath }
        assertions.checkSucceeds { assert this.protectedPathYPoint == expectedYFlowExtended.protectedPathYPoint }
        assertions.checkSucceeds { assert this.diverseWithFlows.sort() == expectedYFlowExtended.diverseWithFlows.sort() }
        assertions.checkSucceeds { assert this.diverseWithYFlows.sort() == expectedYFlowExtended.diverseWithYFlows.sort() }
        assertions.checkSucceeds { assert this.diverseWithHaFlows.sort() == expectedYFlowExtended.diverseWithHaFlows.sort() }

        assertions.checkSucceeds { assert this.sharedEndpoint.switchId == expectedYFlowExtended.sharedEndpoint.switchId }
        assertions.checkSucceeds { assert this.sharedEndpoint.portNumber == expectedYFlowExtended.sharedEndpoint.portNumber }

        this.subFlows.each { actualSubFlow ->
            def expectedSubFlow = expectedYFlowExtended.subFlows.find { it.flowId == actualSubFlow.flowId }
            assertions.checkSucceeds { assert actualSubFlow.flowId == expectedSubFlow.flowId }
            assertions.checkSucceeds { assert actualSubFlow.sharedEndpoint == expectedSubFlow.sharedEndpoint }
            assertions.checkSucceeds { assert actualSubFlow.endpoint.switchId == expectedSubFlow.endpoint.switchId }
            assertions.checkSucceeds { assert actualSubFlow.endpoint.portNumber == expectedSubFlow.endpoint.portNumber }
            assertions.checkSucceeds { assert actualSubFlow.endpoint.vlanId == expectedSubFlow.endpoint.vlanId }
            assertions.checkSucceeds { assert actualSubFlow.endpoint.innerVlanId == expectedSubFlow.endpoint.innerVlanId }
            assertions.checkSucceeds { assert actualSubFlow.timeCreate == expectedSubFlow.timeCreate }
            assertions.checkSucceeds { assert actualSubFlow.description == expectedSubFlow.description }
        }
        if (isYPointVerificationIncluded) {
            assertions.checkSucceeds { assert this.yPoint == expectedYFlowExtended.yPoint }
        }

        assertions.verify()
    }
}
