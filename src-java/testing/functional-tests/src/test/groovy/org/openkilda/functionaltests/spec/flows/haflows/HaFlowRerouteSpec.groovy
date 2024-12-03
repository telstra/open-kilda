package org.openkilda.functionaltests.spec.flows.haflows

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.model.stats.HaFlowStats
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowStatus
import org.openkilda.model.history.DumpType
import org.openkilda.testing.service.northbound.model.HaFlowActionType
import org.openkilda.testing.service.traffexam.TraffExamService
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_RAW_BITS
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Slf4j
@Narrative("Verify reroute operations on HA-flows.")
@Tags([HA_FLOW])
class HaFlowRerouteSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowStats haFlowStats

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Shared
    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Tags([ISL_RECOVER_ON_FAIL])
    @Issue("https://github.com/telstra/open-kilda/issues/5647 (hardware)")
    def "Valid HA-flow can be rerouted"() {
        given: "An HA-flow"
        def swT = switchTriplets.all().findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowFactory.getRandom(swT)

        def initialPaths = haFlow.retrievedAllEntityPaths()
        def islToFail = initialPaths.subFlowPaths.first().getInvolvedIsls().first()

        when: "Fail an HA-flow ISL (bring switch port down)"
        islHelper.breakIsl(islToFail)

        then: "The HA-flow was rerouted after reroute delay"
        def newPaths = null
        wait(rerouteDelay + WAIT_OFFSET) {
            def haFlowDetails = haFlow.retrieveDetails()
            assert haFlowDetails.status == FlowState.UP && haFlowDetails.subFlows.every { it.status == FlowStatus.UP }
            newPaths = haFlow.retrievedAllEntityPaths()
            assert newPaths != initialPaths
        }
        newPaths != null
        def timeAfterRerouting = new Date().getTime()

        and: "History has relevant entries about HA-flow reroute"
        haFlow.waitForHistoryEvent(HaFlowActionType.REROUTE)
        def historyRecord = haFlow.getHistory().getEntriesByType(HaFlowActionType.REROUTE)

        verifyAll {
            historyRecord.size() == 1
            historyRecord[0].haFlowId == haFlow.haFlowId
            historyRecord[0].taskId
            historyRecord[0].timestampIso

            historyRecord[0].payloads.action.find {it == HaFlowActionType.REROUTE.getPayloadLastAction()}
            historyRecord[0].payloads.every {it.timestampIso }

            historyRecord[0].dumps.findAll { it.dumpType == DumpType.STATE_BEFORE }.size() == 1
            historyRecord[0].dumps.findAll { it.dumpType == DumpType.STATE_AFTER }.size() == 1
            historyRecord.dumps.flatten().size() == 2
        }

        and: "HA-flow passes validation"
        haFlow.validate().asExpected

        and: "All involved switches pass switch validation"
        def allInvolvedSwitchIds = initialPaths.getInvolvedSwitches() + newPaths.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "Traffic passes through HA-Flow"
        if (swT.isTraffExamAvailable()) {
            assert haFlow.traffExam(traffExamProvider.get()).run().hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "Stats are collected"
        if (swT.isTraffExamAvailable()) {
            wait(STATS_LOGGING_TIMEOUT) {
                assert haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                        REVERSE,
                        haFlow.getSubFlows().shuffled().first()).hasNonZeroValuesAfter(timeAfterRerouting)
                assert haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                        FORWARD,
                        haFlow.getSharedEndpoint()).hasNonZeroValuesAfter(timeAfterRerouting)
            }
        }
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "HA-flow in 'Down' status is rerouted when discovering a new ISL"() {
        given: "An HA-flow"
        def swT = switchTriplets.all().findSwitchTripletWithAlternativeFirstPortPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowFactory.getRandom(swT)

        def initialPaths = haFlow.retrievedAllEntityPaths()
        def subFlowsFirstIsls = initialPaths.subFlowPaths.collect{ it.getInvolvedIsls().first()} as Set
        assert subFlowsFirstIsls.size() == 1, "Selected ISL is not common for both sub-flows (not shared switch)"

        when: "Bring all ports down on the shared switch that are involved in the current and alternative paths"
        def alternativeIsls = (swT.retrieveAvailablePathsEp1() + swT.retrieveAvailablePathsEp2())
                .collect { it.getInvolvedIsls().first() }.unique().findAll { !subFlowsFirstIsls.contains(it) }

        islHelper.breakIsls(alternativeIsls)
        assert haFlow.retrieveDetails().status == FlowState.UP

        //to avoid automatic rerouting an actual flow port is the last one to switch off.
        islHelper.breakIsls(subFlowsFirstIsls)

        then: "The HA-flow goes to 'Down' status"
        haFlow.waitForBeingInState(FlowState.DOWN, rerouteDelay + WAIT_OFFSET)

        when: "Bring all ports up on the shared switch that are involved in the alternative paths"
        alternativeIsls.each { islHelper.restoreIsl(it) } //fails on jenkins if do it asynchronously

        then: "The HA-flow goes to 'Up' state and the HA-flow was rerouted"
        def newPaths = null
        wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            def haFlowDetails = haFlow.retrieveDetails()
            assert haFlowDetails.status == FlowState.UP &&
                    haFlowDetails.subFlows.every { it.status == FlowStatus.UP }
            newPaths = haFlow.retrievedAllEntityPaths()
            assert newPaths != initialPaths
        }

        and: "The first (shared) subFlow's ISl  has been changed due to the ha-Flow reroute"
        def newPathSubFlowsFirstIsls = newPaths.subFlowPaths.collect{ it.getInvolvedIsls().first()} as Set
        newPathSubFlowsFirstIsls != subFlowsFirstIsls

        and: "HA-flow passes validation"
        haFlow.validate().asExpected

        and: "All involved switches pass switch validation"
        def allInvolvedSwitchIds = (initialPaths.getInvolvedSwitches() + newPaths.getInvolvedSwitches()).unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "HA-flow goes to 'Down' status when ISl of the HA-flow fails and there is no alt path to reroute"() {
        given: "An HA-flow without alternative paths"
        def swT = switchTriplets.all().withAllDifferentEndpoints().switchTriplets.find {
            def yPoints = it.findPotentialYPoints()
            yPoints.size() == 1 && yPoints[0] != it.shared.dpId
        }
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowFactory.getRandom(swT)

        def initialPaths = haFlow.retrievedAllEntityPaths()
        def subFlowsFirstIsls = initialPaths.subFlowPaths.collect{ it.getInvolvedIsls().first()}.unique()
        assert subFlowsFirstIsls.size() == 1, "Selected ISL is not common for both sub-flows (not shared switch)"

        and: "All ISL ports on the shared switch that are involved in the alternative HA-flow paths are down"
        def alternativeIsls = (swT.retrieveAvailablePathsEp1() + swT.retrieveAvailablePathsEp2())
                .collect { it.getInvolvedIsls().first() }.unique().findAll { !subFlowsFirstIsls.contains(it) }
        islHelper.breakIsls(alternativeIsls)
        assert haFlow.retrieveDetails().status == FlowState.UP

        when: "Bring port down of ISL which is involved in the current HA-flow paths"
        islHelper.breakIsl(subFlowsFirstIsls.first())

        then: "The HA-flow goes to 'Down' status"
        haFlow.waitForBeingInState(FlowState.DOWN, rerouteDelay + WAIT_OFFSET)
        haFlow.waitForHistoryEvent(HaFlowActionType.REROUTE_FAIL, rerouteDelay + WAIT_OFFSET)
        wait(rerouteDelay + WAIT_OFFSET) {
           assert haFlow.getHistory().getEntriesByType(HaFlowActionType.REROUTE_FAIL).find {
                it.details =~ /Reason: ISL .* become INACTIVE/ && it.taskId.contains("retry #1 ignore_bw true")
            }?.payloads?.find { it.action == HaFlowActionType.REROUTE_FAIL.payloadLastAction}
        }

        and: "All involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(initialPaths.getInvolvedSwitches()).isEmpty()
    }
}
