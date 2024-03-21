package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Slf4j
@Narrative("Verify path swap operations on y-flows.")
class YFlowPathSwapSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Autowired @Shared
    FlowStats flowStats

    //Protected path of subflow2 can have common ISLs with main path of subflow1. Thus, after breaking random ISL on
    //subflow1 main path, subflow2 can be either in Up state, or in Degraded, and both are valid
    final static List<String> upOrDegradedState = [FlowState.UP, FlowState.DEGRADED].collect{it.getState()}
    final static List<String> upOrDownState = [FlowState.UP, FlowState.DOWN].collect{it.getState()}

    def "Able to swap main and protected paths manually"() {
        given: "A Y-Flow with protected paths"
        def swT = findSwitchTripletForYFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")

        def yFlow = yFlowFactory.getBuilder(swT).withProtectedPath(true).build()
        yFlow = yFlow.waitForBeingInState(FlowState.UP)
        assert yFlow.protectedPathYPoint

        and: "Current paths are not equal to protected paths"
        def initialPath = yFlow.retrieveAllEntityPaths()
        initialPath.subFlowPaths.each {
            assert it.getCommonIslsWithProtected().isEmpty()
        }

        when: "Swap Y-Flow paths"
        yFlow.swap()
        yFlow.waitForBeingInState(FlowState.UP, PROTECTED_PATH_INSTALLATION_TIME)

        then: "The sub-flows are switched to protected paths"
        yFlow.subFlows.each { subFlow ->
            assert northboundV2.getFlowStatus(subFlow.flowId).status == FlowState.UP
        }
        def updatedPath = yFlow.retrieveAllEntityPaths()
        updatedPath.subFlowPaths.each { subFlowPath ->
            assert subFlowPath.path.forward == initialPath.subFlowPaths.find { it.flowId == subFlowPath.flowId }.protectedPath.forward
            assert subFlowPath.protectedPath.forward == initialPath.subFlowPaths.find { it.flowId == subFlowPath.flowId }.path.forward
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        and: "YFlow and related sub-flows are valid"
        yFlow.validate().asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        def involvedSwitches = updatedPath.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        def exam = yFlow.traffExam(traffExam, yFlow.maximumBandwidth, 10)
        examReports = withPool {
            [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                traffExam.waitExam(direction)
            }
        }
        statsHelper."force kilda to collect stats"()

        then: "Traffic flows on both sub-flows, but does not exceed the Y-Flow bandwidth restriction (~halves for each sub-flow)"
        examReports.each { report ->
            assert report.hasTraffic(), report.exam
        }

        and: "Y-Flow and subflows stats are available (flow.raw.bytes)"
        def subflow = yFlow.getSubFlows().shuffled().first()
        def subflowId = subflow.getFlowId()
        def dstSwitchId = subflow.getEndpoint().getSwitchId()
        def flowInfo = database.getFlow(subflowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            def stats = flowStats.of(subflowId)
            assert stats.get(FLOW_RAW_BYTES, dstSwitchId, mainForwardCookie).hasNonZeroValues()
            assert stats.get(FLOW_RAW_BYTES, dstSwitchId, mainReverseCookie).hasNonZeroValues()
        }

        cleanup:
        yFlow && yFlow.delete()
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "System is able to switch a y-flow to protected paths"() {
        given: "A y-flow with protected paths"
        def swT = findSwitchTripletForYFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlow = yFlowFactory.getBuilder(swT).withProtectedPath(true).build()
        yFlow = yFlow.waitForBeingInState(FlowState.UP)
        assert yFlow.protectedPathYPoint

        and: "Current paths are not equal to protected paths"
        def initialPath = yFlow.retrieveAllEntityPaths()
        initialPath.subFlowPaths.each {
            assert it.getCommonIslsWithProtected().isEmpty()
        }

        and: "Other ISLs have not enough bandwidth to host the flows in case of reroute"
        List<Isl> yFlowIsls = initialPath.subFlowPaths.collectMany { subFlow ->
            subFlow.getInvolvedIsls().collectMany {[it, it.reversed] }}.unique()

        database.updateIslsMaxBandwidth(yFlowIsls, yFlow.maximumBandwidth)
        database.updateIslsAvailableBandwidth(yFlowIsls, 0)

        List<Isl> alternativeIsls = (swT.pathsEp1 + swT.pathsEp2).collectMany { pathHelper.getInvolvedIsls(it) }
                .collectMany { [it, it.reversed] }.unique()
        alternativeIsls.removeAll(yFlowIsls)

        database.updateIslsMaxBandwidth(alternativeIsls, yFlow.maximumBandwidth - 1)
        database.updateIslsAvailableBandwidth(alternativeIsls, 0)

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = initialPath.subFlowPaths.first().path.forward.getInvolvedIsls().last()
        islHelper.breakIsl(islToBreak)

        then: "The sub-flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert yFlow.retrieveDetails().status == FlowState.DEGRADED
        }
        verifyAll(northbound.getFlow(initialPath.subFlowPaths.first().flowId)) {
            status == FlowState.DEGRADED.toString()
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Down"
        }
        verifyAll(northbound.getFlow(initialPath.subFlowPaths.last().flowId)) {
            upOrDegradedState.contains(status)
            flowStatusDetails.mainFlowPathStatus == "Up"
            upOrDownState.contains(flowStatusDetails.protectedFlowPathStatus)
        }

        def updatedPathAfterPortDown = yFlow.retrieveAllEntityPaths()
        updatedPathAfterPortDown.subFlowPaths.each { subFlowPath ->
            assert subFlowPath.path.forward == initialPath.subFlowPaths.find { it.flowId == subFlowPath.flowId }.protectedPath.forward
            assert subFlowPath.protectedPath.forward == initialPath.subFlowPaths.find { it.flowId == subFlowPath.flowId }.path.forward
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        and: "YFlow and related sub-flows are valid"
        yFlow.validate().asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        def involvedSwitches = updatedPathAfterPortDown.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        when: "Restore port status"
        islHelper.restoreIsl(islToBreak)

        then: "Paths of the y-flow is not changed"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.getFlow(initialPath.subFlowPaths.first().flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
            verifyAll(northbound.getFlow(initialPath.subFlowPaths.last().flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
        }

        def updatedPathAfterPortUp = yFlow.retrieveAllEntityPaths()
        updatedPathAfterPortUp.subFlowPaths.each { subFlowPath ->
            assert subFlowPath.path.forward == updatedPathAfterPortDown.subFlowPaths.find { it.flowId == subFlowPath.flowId }.path.forward
            assert subFlowPath.protectedPath.forward == updatedPathAfterPortDown.subFlowPaths.find { it.flowId == subFlowPath.flowId }.protectedPath.forward
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        and: "All involved switches passes switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(updatedPathAfterPortUp.getInvolvedSwitches()).isEmpty()

        cleanup: "Revert system to original state"
        yFlow && yFlow.delete()
        islHelper.restoreIsl(islToBreak)
        database.resetIslsBandwidth(topology.getIslsForActiveSwitches().collectMany { [it, it.reversed] })
        database.resetCosts(topology.isls)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for a flow without protected path"() {
        given: "A y-flow without protected path"
        def swT = topologyHelper.switchTriplets[0]
        assumeTrue(swT != null, "No suiting switches found.")

        def yFlow = yFlowFactory.getRandom(swT)
        assert !yFlow.protectedPathYPoint

        when: "Try to swap paths for y-flow that doesn't have a protected path"
        yFlow.swap()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        def errorDescription = exc.responseBodyAsString.to(MessageError).errorDescription
        errorDescription == "Could not swap y-flow paths: sub-flow ${yFlow.subFlows[0].flowId} doesn't have a protected path" ||
                errorDescription == "Could not swap y-flow paths: sub-flow ${yFlow.subFlows[1].flowId} doesn't have a protected path"

        cleanup: "Revert system to original state"
        yFlow && yFlow.delete()
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap paths for a non-existent y-flow"() {
        when: "Try to swap path on a non-existent y-flow"
        northboundV2.swapYFlowPaths(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Y-flow $NON_EXISTENT_FLOW_ID not found"
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "Unable to swap paths for an inactive y-flow"() {
        given: "A y-flow with protected paths"
        def swT = findSwitchTripletForYFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")

        def yFlow = yFlowFactory.getBuilder(swT).withProtectedPath(true).build()
        yFlow = yFlow.waitForBeingInState(FlowState.UP)
        assert yFlow.protectedPathYPoint

        and: "Current paths are not equal to protected paths"
        def initialPath = yFlow.retrieveAllEntityPaths()
        initialPath.subFlowPaths.each {
            assert it.getCommonIslsWithProtected().isEmpty()
        }

        and: "Other ISLs have not enough bandwidth to host the flows in case of reroute"
        List<Isl> yFlowIsls = initialPath.subFlowPaths.collectMany { subFlow ->
            subFlow.getInvolvedIsls().collectMany {[it, it.reversed] }}.unique()
        database.updateIslsMaxBandwidth(yFlowIsls, yFlow.maximumBandwidth)
        database.updateIslsAvailableBandwidth(yFlowIsls, 0)

        List<Isl> alternativeIsls = (swT.pathsEp1 + swT.pathsEp2).collectMany { pathHelper.getInvolvedIsls(it) }
                .collectMany { [it, it.reversed] }.unique()
        alternativeIsls.removeAll(yFlowIsls)
        database.updateIslsMaxBandwidth(alternativeIsls, yFlow.maximumBandwidth - 1)
        database.updateIslsAvailableBandwidth(alternativeIsls, 0)

        when: "Break ISL on the protected path (bring port down) to make it INACTIVE"
        def islToBreak = initialPath.subFlowPaths.first().path.forward.getInvolvedIsls().last()
        islHelper.breakIsl(islToBreak)

        then: "The sub-flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert yFlow.retrieveDetails().status == FlowState.DEGRADED
        }
        verifyAll(northbound.getFlow(initialPath.subFlowPaths.first().flowId)) {
            status == FlowState.DEGRADED.toString()
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Down"
        }
        verifyAll(northbound.getFlow(initialPath.subFlowPaths.first().flowId)) {
            upOrDegradedState.contains(status)
            flowStatusDetails.mainFlowPathStatus == "Up"
            upOrDownState.contains(flowStatusDetails.protectedFlowPathStatus)
        }

        def updatedPathAfterPortDown = yFlow.retrieveAllEntityPaths()
        updatedPathAfterPortDown.subFlowPaths.each { subFlowPath ->
            assert subFlowPath.path.forward == initialPath.subFlowPaths.find { it.flowId == subFlowPath.flowId }.protectedPath.forward
            assert subFlowPath.protectedPath.forward == initialPath.subFlowPaths.find { it.flowId == subFlowPath.flowId }.path.forward
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        when: "Try to swap paths when main/protected paths are not available"
        yFlow.swap()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap y-flow paths: the protected path of sub-flow ${initialPath.subFlowPaths.first().flowId} is not in ACTIVE state, but in INACTIVE/INACTIVE (forward/reverse) state"

        when: "Restore port status"
        islHelper.restoreIsl(islToBreak)

        then: "Paths of the y-flow is not changed"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.getFlow(initialPath.subFlowPaths.first().flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
            verifyAll(northbound.getFlow(initialPath.subFlowPaths.last().flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
        }

        def updatedPathAfterPortUp = yFlow.retrieveAllEntityPaths()
        updatedPathAfterPortUp.subFlowPaths.each { subFlowPath ->
            assert subFlowPath.path.forward == updatedPathAfterPortDown.subFlowPaths.find { it.flowId == subFlowPath.flowId }.path.forward
            assert subFlowPath.protectedPath.forward == updatedPathAfterPortDown.subFlowPaths.find { it.flowId == subFlowPath.flowId }.protectedPath.forward
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        cleanup: "Revert system to original state"
        yFlow && yFlow.delete()
        islHelper.restoreIsl(islToBreak)
        database.resetIslsBandwidth(topology.getIslsForActiveSwitches().collectMany { [it, it.reversed] })
        database.resetCosts(topology.isls)
    }

    SwitchTriplet findSwitchTripletForYFlowWithProtectedPaths() {
        return topologyHelper.getAllNotNeighbouringSwitchTriplets().find {
            def ep1paths = it.pathsEp1.findAll { path -> !path.any { node -> node.switchId == it.ep2.dpId } }
                    .unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.findAll { path -> !path.any { node -> node.switchId == it.ep1.dpId } }
                    .unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = topologyHelper.findPotentialYPoints(it)

            yPoints.size() == 1 && yPoints[0] != it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
    }
}
