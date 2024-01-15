package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.model.stats.HaFlowStats

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_RAW_BITS
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT

@Narrative("Verify path swap operations on HA-flows.")
class HaFlowPathSwapSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    HaFlowStats haFlowStats

    static def convertPaths(HaFlowPaths haFlowPaths) {
        return [PathHelper.convert(haFlowPaths.subFlowPaths[0].forward),
                PathHelper.convert(haFlowPaths.subFlowPaths[0].protectedPath.forward),
                PathHelper.convert(haFlowPaths.subFlowPaths[1].forward),
                PathHelper.convert(haFlowPaths.subFlowPaths[1].protectedPath.forward)]
    }

    def "Able to swap main and protected paths manually"() {
        given: "An HA-flow with protected paths"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT).tap { allocateProtectedPath = true }
        def createdHaFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def haFlowId = createdHaFlow.haFlowId

        and: "Current paths are not equal to protected paths"
        def haFlowPathInfoBefore = northboundV2.getHaFlowPaths(haFlowId)
        def (subFlow1PrimaryBefore, subFlow1ProtectedBefore, subFlow2PrimaryBefore, subFlow2ProtectedBefore)
        = convertPaths(haFlowPathInfoBefore)
        assert subFlow1PrimaryBefore != subFlow1ProtectedBefore
        assert subFlow2PrimaryBefore != subFlow2ProtectedBefore

        when: "Swap HA-flow paths"
        northboundV2.swapHaFlowPaths(haFlowId)
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            def haFlow = northboundV2.getHaFlow(haFlowId)
            assert haFlow.status == FlowState.UP.toString()
            haFlow.subFlows.each {
                it.status == FlowState.UP.toString()
            }
        }
        def timeAfterSwap = new Date().getTime()

        then: "The sub-flows are switched to protected paths"
        def haFlowPathInfoAfter = northboundV2.getHaFlowPaths(haFlowId)
        def (subFlow1PrimaryAfter, subFlow1ProtectedAfter, subFlow2PrimaryAfter, subFlow2ProtectedAfter)
        = convertPaths(haFlowPathInfoAfter)
        assert subFlow1PrimaryAfter != subFlow1ProtectedAfter
        assert subFlow2PrimaryAfter != subFlow2ProtectedAfter
        assert subFlow1PrimaryAfter == subFlow1ProtectedBefore
        assert subFlow2PrimaryAfter == subFlow2ProtectedBefore
        assert subFlow1ProtectedAfter == subFlow1PrimaryBefore
        assert subFlow2ProtectedAfter == subFlow2PrimaryBefore

        and: "Ha-Flow and related sub-flows are valid"
        northboundV2.validateHaFlow(haFlowId).asExpected

        and: "All involved switches pass switch validation"
        def involvedSwitches = haFlowHelper.getInvolvedSwitches(haFlowPathInfoAfter)
        switchHelper.synchronizeAndGetFixedEntries(involvedSwitches).isEmpty()

        and: "Traffic passes through HA-Flow"
        if (swT.isHaTraffExamAvailable()) {
            assert haFlowHelper.getTraffExam(createdHaFlow).run().hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "Stats are collected"
        if (swT.isHaTraffExamAvailable()) {
            Wrappers.wait(STATS_LOGGING_TIMEOUT) {
                assert haFlowStats.of(createdHaFlow.getHaFlowId()).get(HA_FLOW_RAW_BITS,
                        REVERSE,
                        createdHaFlow.getSubFlows().shuffled().first().getEndpoint()).hasNonZeroValuesAfter(timeAfterSwap)
                assert haFlowStats.of(createdHaFlow.getHaFlowId()).get(HA_FLOW_RAW_BITS,
                        FORWARD,
                        createdHaFlow.getSharedEndpoint()).hasNonZeroValuesAfter(timeAfterSwap)
            }
        }

        cleanup:
        createdHaFlow && haFlowHelper.deleteHaFlow(createdHaFlow.haFlowId)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for an HA-flow without protected path"() {
        given: "An HA-flow without protected path"
        def swT = topologyHelper.switchTriplets[0]
        assumeTrue(swT != null, "No suiting switches found.")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        haFlowRequest.allocateProtectedPath = false
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        assert !haFlow.allocateProtectedPath

        when: "Try to swap paths for HA-flow that doesn't have a protected path"
        northboundV2.swapHaFlowPaths(haFlow.haFlowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        def errorDescription = exc.responseBodyAsString.to(MessageError).errorDescription
        errorDescription == "Could not swap paths: HA-flow ${haFlow.haFlowId} doesn't have protected path"

        cleanup: "Revert system to original state"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap paths for a non-existent Ha-flow"() {
        when: "Try to swap path on a non-existent Ha-flow"
        northboundV2.swapHaFlowPaths(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap paths: HA-flow $NON_EXISTENT_FLOW_ID not found"
    }
}
