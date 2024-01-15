package org.openkilda.functionaltests.spec.flows.yflows

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL

import org.openkilda.functionaltests.model.stats.FlowStats

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.openkilda.testing.tools.FlowTrafficExamBuilder

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
    YFlowHelper yFlowHelper
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
        given: "A y-flow with protected paths"
        def swT = findSwitchTripletForYFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap { allocateProtectedPath = true }
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert yFlow.protectedPathYPoint

        and: "Current paths are not equal to protected paths"
        def sFlow1 = yFlow.subFlows[0]
        def sFlow1PathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1InitialPrimary = PathHelper.convert(sFlow1PathInfo)
        assert sFlow1PathInfo.protectedPath
        def sFlow1InitialProtected = PathHelper.convert(sFlow1PathInfo.protectedPath)
        assert sFlow1InitialPrimary != sFlow1InitialProtected

        def sFlow2 = yFlow.subFlows[1]
        def sFlow2PathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2InitialPrimary = PathHelper.convert(sFlow2PathInfo)
        assert sFlow2PathInfo.protectedPath
        def sFlow2InitialProtected = PathHelper.convert(sFlow2PathInfo.protectedPath)
        assert sFlow2InitialPrimary != sFlow2InitialProtected

        when: "Swap y-flow paths"
        northboundV2.swapYFlowPaths(yFlow.YFlowId)
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            yFlow = northboundV2.getYFlow(yFlow.YFlowId)
            assert yFlow.status == FlowState.UP.toString()
        }

        then: "The sub-flows are switched to protected paths"
        yFlow.subFlows.each { subFlow ->
            assert northboundV2.getFlowStatus(subFlow.flowId).status == FlowState.UP
        }
        def sFlow1AfterPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1AfterPrimary = PathHelper.convert(sFlow1AfterPathInfo)
        assert sFlow1AfterPathInfo.protectedPath
        def sFlow1AfterProtected = PathHelper.convert(sFlow1AfterPathInfo.protectedPath)
        assert sFlow1AfterPrimary != sFlow1AfterProtected
        assert sFlow1InitialPrimary == sFlow1AfterProtected
        assert sFlow1InitialProtected == sFlow1AfterPrimary

        def sFlow2AfterPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2AfterPrimary = PathHelper.convert(sFlow2AfterPathInfo)
        assert sFlow2AfterPathInfo.protectedPath
        def sFlow2AfterProtected = PathHelper.convert(sFlow2AfterPathInfo.protectedPath)
        assert sFlow2AfterPrimary != sFlow2AfterProtected
        assert sFlow2InitialPrimary == sFlow2AfterProtected
        assert sFlow2InitialProtected == sFlow2AfterPrimary

        and: "YFlow and related sub-flows are valid"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        def yFlowPaths = northboundV2.getYFlowPaths(yFlow.YFlowId)
        def involvedSwitches = pathHelper.getInvolvedYSwitches(yFlowPaths)*.getDpId()
        switchHelper.synchronizeAndGetFixedEntries(involvedSwitches).isEmpty()

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildYFlowExam(yFlow, yFlow.maximumBandwidth, 10)
        examReports = withPool {
            [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                traffExam.waitExam(direction)
            }
        }
        statsHelper."force kilda to collect stats"()

        then: "Traffic flows on both sub-flows, but does not exceed the y-flow bandwidth restriction (~halves for each sub-flow)"
        examReports.each { report ->
            assert report.hasTraffic(), report.exam
        }

        and: "Y-flow and subflows stats are available (flow.raw.bytes)"
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
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "System is able to switch a y-flow to protected paths"() {
        given: "A y-flow with protected paths"
        def swT = findSwitchTripletForYFlowWithProtectedPaths()
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap { allocateProtectedPath = true }
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert yFlow.protectedPathYPoint

        and: "Current paths are not equal to protected paths"
        def sFlow1 = yFlow.subFlows[0]
        def sFlow1PathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1InitialPrimary = PathHelper.convert(sFlow1PathInfo)
        assert sFlow1PathInfo.protectedPath
        def sFlow1InitialProtected = PathHelper.convert(sFlow1PathInfo.protectedPath)
        assert sFlow1InitialPrimary != sFlow1InitialProtected

        def sFlow2 = yFlow.subFlows[1]
        def sFlow2PathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2InitialPrimary = PathHelper.convert(sFlow2PathInfo)
        assert sFlow2PathInfo.protectedPath
        def sFlow2InitialProtected = PathHelper.convert(sFlow2PathInfo.protectedPath)
        assert sFlow2InitialPrimary != sFlow2InitialProtected

        and: "Other ISLs have not enough bandwidth to host the flows in case of reroute"
        [sFlow1InitialPrimary, sFlow1InitialProtected, sFlow2InitialPrimary, sFlow2InitialProtected].each { path ->
            pathHelper.getInvolvedIsls(path).collectMany { [it, it.reversed] }.each {
                database.updateIslMaxBandwidth(it, yFlow.maximumBandwidth)
                database.updateIslAvailableBandwidth(it, 0)
            }
        }
        def ep1OtherIsls = swT.pathsEp1.findAll { it != sFlow1InitialPrimary && it != sFlow1InitialProtected }
                .collectMany { pathHelper.getInvolvedIsls(it) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        ep1OtherIsls.collectMany { [it, it.reversed] }.each {
            database.updateIslMaxBandwidth(it, yFlow.maximumBandwidth - 1)
            database.updateIslAvailableBandwidth(it, 0)
        }
        def ep2OtherIsls = swT.pathsEp1.findAll { it != sFlow2InitialPrimary && it != sFlow2InitialProtected }
                .collectMany { pathHelper.getInvolvedIsls(it) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        ep2OtherIsls.collectMany { [it, it.reversed] }.each {
            database.updateIslMaxBandwidth(it, yFlow.maximumBandwidth - 1)
            database.updateIslAvailableBandwidth(it, 0)
        }

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = pathHelper.getInvolvedIsls(sFlow1InitialPrimary)[-1]
        def portDown = antiflap.portDown(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getLink(islToBreak).state == IslChangeType.FAILED }

        then: "The sub-flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.DEGRADED.toString()
        }
        verifyAll(northbound.getFlow(sFlow1.flowId)) {
            status == FlowState.DEGRADED.toString()
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Down"
        }
        verifyAll(northbound.getFlow(sFlow2.flowId)) {
            upOrDegradedState.contains(status)
            flowStatusDetails.mainFlowPathStatus == "Up"
            upOrDownState.contains(flowStatusDetails.protectedFlowPathStatus)
        }

        def sFlow1AfterPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1AfterPrimary = PathHelper.convert(sFlow1AfterPathInfo)
        assert sFlow1AfterPathInfo.protectedPath
        def sFlow1AfterProtected = PathHelper.convert(sFlow1AfterPathInfo.protectedPath)
        assert sFlow1AfterPrimary != sFlow1AfterProtected
        assert sFlow1InitialPrimary == sFlow1AfterProtected
        assert sFlow1InitialProtected == sFlow1AfterPrimary

        def sFlow2AfterPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2AfterPrimary = PathHelper.convert(sFlow2AfterPathInfo)
        assert sFlow2AfterPathInfo.protectedPath
        def sFlow2AfterProtected = PathHelper.convert(sFlow2AfterPathInfo.protectedPath)
        assert sFlow2AfterPrimary != sFlow2AfterProtected
        assert sFlow2InitialPrimary == sFlow2AfterProtected
        assert sFlow2InitialProtected == sFlow2AfterPrimary

        and: "YFlow and related sub-flows are valid"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        def yFlowPaths = northboundV2.getYFlowPaths(yFlow.YFlowId)
        def involvedSwitches = pathHelper.getInvolvedYSwitches(yFlowPaths)*.getDpId()
        switchHelper.synchronizeAndGetFixedEntries(involvedSwitches).isEmpty()

        when: "Restore port status"
        def portUp = antiflap.portUp(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        then: "Paths of the y-flow is not changed"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.getFlow(sFlow1.flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
            verifyAll(northbound.getFlow(sFlow2.flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
        }

        def sFlow1LastPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1LastPrimary = PathHelper.convert(sFlow1LastPathInfo)
        def sFlow1LastProtected = PathHelper.convert(sFlow1LastPathInfo.protectedPath)
        assert sFlow1LastPrimary != sFlow1LastProtected
        assert sFlow1InitialPrimary == sFlow1LastProtected
        assert sFlow1InitialProtected == sFlow1LastPrimary

        def sFlow2LastPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2LastPrimary = PathHelper.convert(sFlow2LastPathInfo)
        def sFlow2LastProtected = PathHelper.convert(sFlow2LastPathInfo.protectedPath)
        assert sFlow2LastPrimary != sFlow2LastProtected
        assert sFlow2InitialPrimary == sFlow2LastProtected
        assert sFlow2InitialProtected == sFlow2LastPrimary

        and: "All involved switches passes switch validation"
        def yFlowPathsAfter = northboundV2.getYFlowPaths(yFlow.YFlowId)
        switchHelper.synchronizeAndGetFixedEntries(pathHelper.getInvolvedYSwitches(yFlowPathsAfter)*.getDpId()).isEmpty()

        cleanup: "Revert system to original state"
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        portDown && !portUp && antiflap.portUp(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        topology.getIslsForActiveSwitches().collectMany { [it, it.reversed] }.each { database.resetIslBandwidth(it) }
        islToBreak && Wrappers.wait(WAIT_OFFSET) { assert northbound.getLink(islToBreak).state == IslChangeType.DISCOVERED }
        database.resetCosts(topology.isls)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for a flow without protected path"() {
        given: "A y-flow without protected path"
        def swT = topologyHelper.switchTriplets[0]
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert !yFlow.protectedPathYPoint

        when: "Try to swap paths for y-flow that doesn't have a protected path"
        northboundV2.swapYFlowPaths(yFlow.YFlowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        def errorDescription = exc.responseBodyAsString.to(MessageError).errorDescription
        errorDescription == "Could not swap y-flow paths: sub-flow ${yFlow.subFlows[0].flowId} doesn't have a protected path" ||
                errorDescription == "Could not swap y-flow paths: sub-flow ${yFlow.subFlows[1].flowId} doesn't have a protected path"

        cleanup: "Revert system to original state"
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
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
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap { allocateProtectedPath = true }
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert yFlow.protectedPathYPoint

        and: "Current paths are not equal to protected paths"
        def sFlow1 = yFlow.subFlows[0]
        def sFlow1PathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1InitialPrimary = PathHelper.convert(sFlow1PathInfo)
        assert sFlow1PathInfo.protectedPath
        def sFlow1InitialProtected = PathHelper.convert(sFlow1PathInfo.protectedPath)
        assert sFlow1InitialPrimary != sFlow1InitialProtected

        def sFlow2 = yFlow.subFlows[1]
        def sFlow2PathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2InitialPrimary = PathHelper.convert(sFlow2PathInfo)
        assert sFlow2PathInfo.protectedPath
        def sFlow2InitialProtected = PathHelper.convert(sFlow2PathInfo.protectedPath)
        assert sFlow2InitialPrimary != sFlow2InitialProtected

        and: "Other ISLs have not enough bandwidth to host the flows in case of reroute"
        [sFlow1InitialPrimary, sFlow1InitialProtected, sFlow2InitialPrimary, sFlow2InitialProtected].each { path ->
            pathHelper.getInvolvedIsls(path).collectMany { [it, it.reversed] }.each {
                database.updateIslMaxBandwidth(it, yFlow.maximumBandwidth)
                database.updateIslAvailableBandwidth(it, 0)
            }
        }
        def ep1OtherIsls = swT.pathsEp1.findAll { it != sFlow1InitialPrimary && it != sFlow1InitialProtected }
                .collectMany { pathHelper.getInvolvedIsls(it) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        ep1OtherIsls.collectMany { [it, it.reversed] }.each {
            database.updateIslMaxBandwidth(it, yFlow.maximumBandwidth - 1)
            database.updateIslAvailableBandwidth(it, 0)
        }
        def ep2OtherIsls = swT.pathsEp1.findAll { it != sFlow2InitialPrimary && it != sFlow2InitialProtected }
                .collectMany { pathHelper.getInvolvedIsls(it) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        ep2OtherIsls.collectMany { [it, it.reversed] }.each {
            database.updateIslMaxBandwidth(it, yFlow.maximumBandwidth - 1)
            database.updateIslAvailableBandwidth(it, 0)
        }

        when: "Break ISL on the protected path (bring port down) to make it INACTIVE"
        def islToBreak = pathHelper.getInvolvedIsls(sFlow1InitialProtected)[-1]
        def portDown = antiflap.portDown(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getLink(islToBreak).state == IslChangeType.FAILED }

        then: "The sub-flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.DEGRADED.toString()
        }
        verifyAll(northbound.getFlow(sFlow1.flowId)) {
            status == FlowState.DEGRADED.toString()
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Down"
        }
        verifyAll(northbound.getFlow(sFlow2.flowId)) {
            upOrDegradedState.contains(status)
            flowStatusDetails.mainFlowPathStatus == "Up"
            upOrDownState.contains(flowStatusDetails.protectedFlowPathStatus)
        }

        def sFlow1AfterPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1AfterPrimary = PathHelper.convert(sFlow1AfterPathInfo)
        assert sFlow1AfterPathInfo.protectedPath
        def sFlow1AfterProtected = PathHelper.convert(sFlow1AfterPathInfo.protectedPath)
        assert sFlow1AfterPrimary != sFlow1AfterProtected
        assert sFlow1InitialPrimary == sFlow1AfterPrimary
        assert sFlow1InitialProtected == sFlow1AfterProtected

        def sFlow2AfterPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2AfterPrimary = PathHelper.convert(sFlow2AfterPathInfo)
        assert sFlow2AfterPathInfo.protectedPath
        def sFlow2AfterProtected = PathHelper.convert(sFlow2AfterPathInfo.protectedPath)
        assert sFlow2AfterPrimary != sFlow2AfterProtected
        assert sFlow2InitialPrimary == sFlow2AfterPrimary
        assert sFlow2InitialProtected == sFlow2AfterProtected

        when: "Try to swap paths when main/protected paths are not available"
        northboundV2.swapYFlowPaths(yFlow.YFlowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap y-flow paths: the protected path of sub-flow $sFlow1.flowId is not in ACTIVE state, but in INACTIVE/INACTIVE (forward/reverse) state"

        when: "Restore port status"
        def portUp = antiflap.portUp(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        then: "Paths of the y-flow is not changed"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.getFlow(sFlow1.flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
            verifyAll(northbound.getFlow(sFlow2.flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
        }

        def sFlow1LastPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1LastPrimary = PathHelper.convert(sFlow1LastPathInfo)
        def sFlow1LastProtected = PathHelper.convert(sFlow1LastPathInfo.protectedPath)
        assert sFlow1LastPrimary != sFlow1LastProtected
        assert sFlow1InitialPrimary == sFlow1LastPrimary
        assert sFlow1InitialProtected == sFlow1LastProtected

        def sFlow2LastPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2LastPrimary = PathHelper.convert(sFlow2LastPathInfo)
        def sFlow2LastProtected = PathHelper.convert(sFlow2LastPathInfo.protectedPath)
        assert sFlow2LastPrimary != sFlow2LastProtected
        assert sFlow2InitialPrimary == sFlow2LastPrimary
        assert sFlow2InitialProtected == sFlow2LastProtected

        cleanup: "Revert system to original state"
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        portDown && !portUp && antiflap.portUp(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        topology.getIslsForActiveSwitches().collectMany { [it, it.reversed] }.each { database.resetIslBandwidth(it) }
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getLink(islToBreak).state == IslChangeType.DISCOVERED }
        database.resetCosts(topology.isls)
    }

    SwitchTriplet findSwitchTripletForYFlowWithProtectedPaths() {
        return topologyHelper.switchTriplets.find {
            def ep1paths = it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = topologyHelper.findPotentialYPoints(it)

            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    yPoints.size() == 1 && yPoints[0] != it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    ep1paths.every { path -> !path.any { node -> node.getSwitchId() == it.ep2.getDpId() } } &&
                    ep2paths.every { path -> !path.any { node -> node.getSwitchId() == it.ep1.getDpId() } } &&
                    ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
    }
}
