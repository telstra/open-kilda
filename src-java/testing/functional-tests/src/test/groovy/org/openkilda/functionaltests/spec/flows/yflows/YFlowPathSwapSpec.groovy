package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.transform.Memoized
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

    @Tidy
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
        def sFlow1InitialPrimary = pathHelper.convert(sFlow1PathInfo)
        assert sFlow1PathInfo.protectedPath
        def sFlow1InitialProtected = pathHelper.convert(sFlow1PathInfo.protectedPath)
        assert sFlow1InitialPrimary != sFlow1InitialProtected

        def sFlow2 = yFlow.subFlows[1]
        def sFlow2PathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2InitialPrimary = pathHelper.convert(sFlow2PathInfo)
        assert sFlow2PathInfo.protectedPath
        def sFlow2InitialProtected = pathHelper.convert(sFlow2PathInfo.protectedPath)
        assert sFlow2InitialPrimary != sFlow2InitialProtected

        when: "Swap y-flow paths"
        northboundV2.swapYFlowPaths(yFlow.YFlowId)
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
        }

        then: "The sub-flows are switched to protected paths"
        yFlow.subFlows.each { subFlow ->
            assert northboundV2.getFlowStatus(subFlow.flowId).status == FlowState.UP
        }
        def sFlow1AfterPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1AfterPrimary = pathHelper.convert(sFlow1AfterPathInfo)
        assert sFlow1AfterPathInfo.protectedPath
        def sFlow1AfterProtected = pathHelper.convert(sFlow1AfterPathInfo.protectedPath)
        assert sFlow1AfterPrimary != sFlow1AfterProtected
        assert sFlow1InitialPrimary == sFlow1AfterProtected
        assert sFlow1InitialProtected == sFlow1AfterPrimary

        def sFlow2AfterPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2AfterPrimary = pathHelper.convert(sFlow2AfterPathInfo)
        assert sFlow2AfterPathInfo.protectedPath
        def sFlow2AfterProtected = pathHelper.convert(sFlow2AfterPathInfo.protectedPath)
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
        def involvedSwitches = pathHelper.getInvolvedYSwitches(yFlowPaths)
        involvedSwitches.each { sw ->
            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def beforeTraffic = new Date()
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildYFlowExam(yFlow, yFlow.maximumBandwidth, 5)
        examReports = withPool {
            [exam.forward1, exam.reverse1, exam.forward2, exam.reverse2].collectParallel { Exam direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                traffExam.waitExam(direction)
            }
        }

        then: "Traffic flows on both sub-flows, but does not exceed the y-flow bandwidth restriction (~halves for each sub-flow)"
        examReports.each { report ->
            assert report.hasTraffic(), report.exam
        }

        and: "Y-flow and subflows stats are available (flow.raw.bytes)"
//        statsHelper.verifyFlowWritesStats(yFlow.YFlowId, beforeTraffic, true)
        yFlow.subFlows.each {
            statsHelper.verifyFlowWritesStats(it.flowId, beforeTraffic, true)
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
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
        def sFlow1InitialPrimary = pathHelper.convert(sFlow1PathInfo)
        assert sFlow1PathInfo.protectedPath
        def sFlow1InitialProtected = pathHelper.convert(sFlow1PathInfo.protectedPath)
        assert sFlow1InitialPrimary != sFlow1InitialProtected

        def sFlow2 = yFlow.subFlows[1]
        def sFlow2PathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2InitialPrimary = pathHelper.convert(sFlow2PathInfo)
        assert sFlow2PathInfo.protectedPath
        def sFlow2InitialProtected = pathHelper.convert(sFlow2PathInfo.protectedPath)
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
            status == FlowState.UP.toString()
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Up"
        }

        def sFlow1AfterPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1AfterPrimary = pathHelper.convert(sFlow1AfterPathInfo)
        assert sFlow1AfterPathInfo.protectedPath
        def sFlow1AfterProtected = pathHelper.convert(sFlow1AfterPathInfo.protectedPath)
        assert sFlow1AfterPrimary != sFlow1AfterProtected
        assert sFlow1InitialPrimary == sFlow1AfterProtected
        assert sFlow1InitialProtected == sFlow1AfterPrimary

        def sFlow2AfterPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2AfterPrimary = pathHelper.convert(sFlow2AfterPathInfo)
        assert sFlow2AfterPathInfo.protectedPath
        def sFlow2AfterProtected = pathHelper.convert(sFlow2AfterPathInfo.protectedPath)
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
        def involvedSwitches = pathHelper.getInvolvedYSwitches(yFlowPaths)
        involvedSwitches.each { sw ->
            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

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
        def sFlow1LastPrimary = pathHelper.convert(sFlow1LastPathInfo)
        def sFlow1LastProtected = pathHelper.convert(sFlow1LastPathInfo.protectedPath)
        assert sFlow1LastPrimary != sFlow1LastProtected
        assert sFlow1InitialPrimary == sFlow1LastProtected
        assert sFlow1InitialProtected == sFlow1LastPrimary

        def sFlow2LastPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2LastPrimary = pathHelper.convert(sFlow2LastPathInfo)
        def sFlow2LastProtected = pathHelper.convert(sFlow2LastPathInfo.protectedPath)
        assert sFlow2LastPrimary != sFlow2LastProtected
        assert sFlow2InitialPrimary == sFlow2LastProtected
        assert sFlow2InitialProtected == sFlow2LastPrimary

        and: "All involved switches passes switch validation"
        def yFlowPathsAfter = northboundV2.getYFlowPaths(yFlow.YFlowId)
        def involvedSwitchesAfter = pathHelper.getInvolvedYSwitches(yFlowPathsAfter)
        involvedSwitchesAfter.each { sw ->
            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        cleanup: "Revert system to original state"
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        portDown && !portUp && antiflap.portUp(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        topology.getIslsForActiveSwitches().collectMany { [it, it.reversed] }.each { database.resetIslBandwidth(it) }
        islToBreak && Wrappers.wait(WAIT_OFFSET) { assert northbound.getLink(islToBreak).state == IslChangeType.DISCOVERED }
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for a flow without protected path"() {
        given: "A y-flow without protected path"
        def swT = topologyHelper.switchTriplets[0]
        assumeTrue(swT != null, "No suiting switches found.")
        print("=" * 100)
        print(swT)
        print("=" * 100)
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert !yFlow.protectedPathYPoint

        when: "Try to swap paths for y-flow that doesn't have protected path"
        northboundV2.swapYFlowPaths(yFlow.YFlowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        def errorDescription = exc.responseBodyAsString.to(MessageError).errorDescription
        errorDescription == "Could not swap y-flow paths: sub-flow ${yFlow.subFlows[0].flowId} doesn't have protected path" ||
                errorDescription == "Could not swap y-flow paths: sub-flow ${yFlow.subFlows[1].flowId} doesn't have protected path"

        cleanup: "Revert system to original state"
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tidy
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

    @Tidy
    @Tags(LOW_PRIORITY)
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
        def sFlow1InitialPrimary = pathHelper.convert(sFlow1PathInfo)
        assert sFlow1PathInfo.protectedPath
        def sFlow1InitialProtected = pathHelper.convert(sFlow1PathInfo.protectedPath)
        assert sFlow1InitialPrimary != sFlow1InitialProtected

        def sFlow2 = yFlow.subFlows[1]
        def sFlow2PathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2InitialPrimary = pathHelper.convert(sFlow2PathInfo)
        assert sFlow2PathInfo.protectedPath
        def sFlow2InitialProtected = pathHelper.convert(sFlow2PathInfo.protectedPath)
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
            status == FlowState.UP.toString()
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Up"
        }

        def sFlow1AfterPathInfo = northbound.getFlowPath(sFlow1.flowId)
        def sFlow1AfterPrimary = pathHelper.convert(sFlow1AfterPathInfo)
        assert sFlow1AfterPathInfo.protectedPath
        def sFlow1AfterProtected = pathHelper.convert(sFlow1AfterPathInfo.protectedPath)
        assert sFlow1AfterPrimary != sFlow1AfterProtected
        assert sFlow1InitialPrimary == sFlow1AfterPrimary
        assert sFlow1InitialProtected == sFlow1AfterProtected

        def sFlow2AfterPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2AfterPrimary = pathHelper.convert(sFlow2AfterPathInfo)
        assert sFlow2AfterPathInfo.protectedPath
        def sFlow2AfterProtected = pathHelper.convert(sFlow2AfterPathInfo.protectedPath)
        assert sFlow2AfterPrimary != sFlow2AfterProtected
        assert sFlow2InitialPrimary == sFlow2AfterPrimary
        assert sFlow2InitialProtected == sFlow2AfterProtected

        when: "Try to swap paths when main/protected paths are not available"
        northboundV2.swapYFlowPaths(yFlow.YFlowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap y-flow paths: protected path of sub-flow $sFlow1.flowId is not in ACTIVE state, but in INACTIVE/INACTIVE state"

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
        def sFlow1LastPrimary = pathHelper.convert(sFlow1LastPathInfo)
        def sFlow1LastProtected = pathHelper.convert(sFlow1LastPathInfo.protectedPath)
        assert sFlow1LastPrimary != sFlow1LastProtected
        assert sFlow1InitialPrimary == sFlow1LastPrimary
        assert sFlow1InitialProtected == sFlow1LastProtected

        def sFlow2LastPathInfo = northbound.getFlowPath(sFlow2.flowId)
        def sFlow2LastPrimary = pathHelper.convert(sFlow2LastPathInfo)
        def sFlow2LastProtected = pathHelper.convert(sFlow2LastPathInfo.protectedPath)
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
            def yPoints = findPotentialYPoints(it)

            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    yPoints.size() == 1 && yPoints[0] != it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    ep1paths.every { path -> !path.any { node -> node.getSwitchId() == it.ep2.getDpId() } } &&
                    ep2paths.every { path -> !path.any { node -> node.getSwitchId() == it.ep1.getDpId() } } &&
                    ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
    }

    @Memoized
    List<Switch> findPotentialYPoints(SwitchTriplet swT) {
        def sortedEp1Paths = swT.pathsEp1.sort { it.size() }
        def potentialEp1Paths = sortedEp1Paths.takeWhile { it.size() == sortedEp1Paths[0].size() }
        def potentialEp2Paths = potentialEp1Paths.collect { potentialEp1Path ->
            def sortedEp2Paths = swT.pathsEp2.sort {
                it.size() - it.intersect(potentialEp1Path).size()
            }
            [path1          : potentialEp1Path,
             potentialPaths2: sortedEp2Paths.takeWhile { it.size() == sortedEp2Paths[0].size() }]
        }
        return potentialEp2Paths.collectMany { path1WithPath2 ->
            path1WithPath2.potentialPaths2.collect { List<PathNode> potentialPath2 ->
                def switches = pathHelper.getInvolvedSwitches(path1WithPath2.path1)
                        .intersect(pathHelper.getInvolvedSwitches(potentialPath2))
                switches ? switches[-1] : null
            }
        }.findAll().unique()
    }
}
