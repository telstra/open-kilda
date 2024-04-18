package org.openkilda.functionaltests.spec.flows.haflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_RAW_BITS
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.model.stats.HaFlowStats
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import jakarta.inject.Provider

@Narrative("Verify path swap operations on HA-flows.")
@Tags([HA_FLOW])
class HaFlowPathSwapSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowStats haFlowStats

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def "Able to swap main and protected paths manually"() {
        given: "An HA-Flow with protected paths"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")
        def haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(true)
                .build().waitForBeingInState(FlowState.UP)

        and: "Current paths are not equal to protected paths"
        def haFlowPathInfoBefore = haFlow.retrievedAllEntityPaths()
        haFlowPathInfoBefore.subFlowPaths.each {subFlowPath ->
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        when: "Swap HA-Flow paths"
        haFlow.swap()
        haFlow.waitForBeingInState(FlowState.UP, PROTECTED_PATH_INSTALLATION_TIME)
        def timeAfterSwap = new Date().getTime()

        then: "The sub-flows are switched to protected paths"
        def haFlowPathInfoAfter = haFlow.retrievedAllEntityPaths()

        haFlowPathInfoAfter.subFlowPaths.each {subFlowPath ->
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        haFlowPathInfoAfter.subFlowPaths.each { subFlow ->
            assert subFlow.path.forward == haFlowPathInfoBefore.subFlowPaths.find { it.flowId == subFlow.flowId}.protectedPath.forward
            assert subFlow.protectedPath.forward == haFlowPathInfoBefore.subFlowPaths.find { it.flowId == subFlow.flowId}.path.forward
        }

        and: "HA-Flow and related sub-flows are valid"
        haFlow.validate().asExpected

        and: "All involved switches pass switch validation"
        def involvedSwitches = haFlowPathInfoAfter.getInvolvedSwitches(true)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        and: "Traffic passes through HA-Flow"
        if (swT.isHaTraffExamAvailable()) {
            assert haFlow.traffExam(traffExamProvider.get()).run().hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "Stats are collected"
        if (swT.isHaTraffExamAvailable()) {
            Wrappers.wait(STATS_LOGGING_TIMEOUT) {
                assert haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                        REVERSE, haFlow.subFlows.shuffled().first().endpoint).hasNonZeroValuesAfter(timeAfterSwap)

                assert haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                        FORWARD, haFlow.sharedEndpoint).hasNonZeroValuesAfter(timeAfterSwap)
            }
        }

        cleanup:
        haFlow && haFlow.delete()
    }

    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for an HA-Flow without protected path"() {
        given: "An HA-Flow without protected path"
        def swT = topologyHelper.switchTriplets[0]
        assumeTrue(swT != null, "No suiting switches found.")
        def haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(false)
                .build().waitForBeingInState(FlowState.UP)
        assert !haFlow.allocateProtectedPath

        when: "Try to swap paths for HA-Flow that doesn't have a protected path"
        haFlow.swap()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        def errorDescription = exc.responseBodyAsString.to(MessageError).errorDescription
        errorDescription == "Could not swap paths: HA-flow ${haFlow.haFlowId} doesn't have protected path"

        cleanup: "Revert system to original state"
        haFlow && haFlow.delete()
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap paths for a non-existent HA-Flow"() {
        when: "Try to swap path on a non-existent HA-Flow"
        northboundV2.swapHaFlowPaths(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap paths: HA-flow $NON_EXISTENT_FLOW_ID not found"
    }
}
