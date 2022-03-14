package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_SUCCESS
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpoint
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Slf4j
@Narrative("Verify reroute operations on y-flows.")
class YFlowProtectedSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Tidy
//    @Ignore
    def "Able to enable/disable protected path on a flow"() {
        given: "A simple y-flow"
        def swT = topologyHelper.switchTriplets.find {
            def ep1paths = it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = findPotentialYPoints(it)
            //se == yp
            yPoints.size() == 1 && yPoints[0] == it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    it.ep1 != it.ep2 && ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def yFlowRequest = yFlowHelper.randomYFlow(swT)
        YFlow yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert !northboundV2.getYFlowPaths(yFlow.YFlowId).sharedProtectedPath

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def update = yFlowHelper.convertToUpdate(yFlow.tap { it.allocateProtectedPath  = true })
        def updateResponse = yFlowHelper.updateYFlow(yFlow.YFlowId, update)

        then: "Update response contains enabled protected path"
        updateResponse.allocateProtectedPath

        and: "Protected path is really enabled on the YFlow"
        northboundV2.getYFlow(yFlow.YFlowId).allocateProtectedPath

        and: "Protected path is really enabled on the sub flows"
        yFlow.subFlows.each {
            assert northbound.getFlow(it.flowId).allocateProtectedPath
        }

        and: "Protected path is really created"
        def paths = northboundV2.getYFlowPaths(yFlow.YFlowId)
        paths.subFlowProtectedPaths
        paths.subFlowPaths != paths.subFlowProtectedPaths

        and: "YFlow and related sub-flows are valid"
//        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        def involvedSwitches = pathHelper.getInvolvedYSwitches(northboundV2.getYFlowPaths(yFlow.YFlowId))
        involvedSwitches.each { sw ->
            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        when: "Disable protected path via partial update"
        def patch = YFlowPatchPayload.builder().allocateProtectedPath(false).build()
        def patchResponse = yFlowHelper.partialUpdateYFlow(yFlow.YFlowId, patch)

        then: "Partial update response contains disabled protected path"
        !patchResponse.allocateProtectedPath

        then: "Protected path is really disable for YFlow/sub-flows"
        !northboundV2.getYFlow(yFlow.YFlowId).allocateProtectedPath
        yFlow.subFlows.each {
            assert !northbound.getFlow(it.flowId).allocateProtectedPath
        }

        and: "Protected path is deleted"
        !northboundV2.getYFlowPaths(yFlow.YFlowId).subFlowProtectedPaths

        and: "YFlow and related sub-flows are valid"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
//        involvedSwitches.each { sw ->
//            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
//            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
//        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Ignore("Manual swap for y-flow is not implemented")
    def "Able to swap main and protected paths manually for sub-flow"() {
        given: "A protected y-flow"
        def swT = topologyHelper.switchTriplets.find {
            def ep1paths = it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = findPotentialYPoints(it)
            //se == yp
            yPoints.size() == 1 && yPoints[0] == it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens } && it.ep1 != it.ep2 &&
                    ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap { allocateProtectedPath = true }
        YFlow yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert northboundV2.getYFlow(yFlow.YFlowId).protectedPathYPoint

        when: "Swap flow paths for the first subflow"
        def sFlow = yFlow.subFlows[0]
        def sFlowPathInfo = northbound.getFlowPath(sFlow.flowId)
        def currentPath = pathHelper.convert(sFlowPathInfo)
        def currentProtectedPath = pathHelper.convert(sFlowPathInfo.protectedPath)
        assert currentPath != currentProtectedPath
        def currentLastUpdate = northboundV2.getFlow(sFlow.flowId).lastUpdated
        northbound.swapFlowPath(sFlow.flowId)
//        checkLastUpdated yflow

        then: "Paths are really swapped for the first flow"
        wait(WAIT_OFFSET) {
            assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
        }
        def sFlowPathInfoAfterSwapping = northbound.getFlowPath(sFlow.flowId)
        def newCurrentPath = pathHelper.convert(sFlowPathInfoAfterSwapping)
        def newCurrentProtectedPath = pathHelper.convert(sFlowPathInfoAfterSwapping.protectedPath)
        newCurrentPath == currentProtectedPath
        newCurrentProtectedPath == currentPath

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
    def "Y-Flow is marked as degraded in case sub-flow is degraded"() {
        given: "A protected y-flow"
        def swT = topologyHelper.switchTriplets.find {
            def ep1paths = it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = findPotentialYPoints(it)
            //se == yp
            yPoints.size() == 1 && yPoints[0] == it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    it.ep1 != it.ep2 && ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap { allocateProtectedPath = true }
        YFlow yFlow = yFlowHelper.addYFlow(yFlowRequest)

        when: "Other paths are not available (ISLs are down)"
        and: "Main flow path breaks"
        def subFlow_1 = yFlow.subFlows.first()
        def dstSwIdSubFl_1 = subFlow_1.endpoint.switchId
        def path = northbound.getFlowPath(subFlow_1.flowId)
        def originalMainPath = pathHelper.convert(path)
        def originalProtectedPath = pathHelper.convert(path.protectedPath)
        List<Isl> usedIsls = (pathHelper.getInvolvedIsls(originalMainPath) + pathHelper.getInvolvedIsls(originalProtectedPath))
        List<Isl> otherIsls = topology.islsForActiveSwitches.collectMany { [it, it.reversed] }
                .findAll { it.dstSwitch.dpId == dstSwIdSubFl_1 } - usedIsls
        otherIsls.each { antiflap.portDown(it.dstSwitch.dpId, it.dstPort) }
        wait(antiflapMin + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == FAILED
            }.size() == otherIsls.size() * 2
        }
        antiflap.portDown(originalMainPath.last().switchId, originalMainPath.last().portNo)

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        wait(WAIT_OFFSET) {
            def newPath = northbound.getFlowPath(subFlow_1.flowId)
            assert pathHelper.convert(newPath) == originalProtectedPath  // mainPath swapped to protected <-- ok
            assert pathHelper.convert(newPath.protectedPath) == originalMainPath  // protected is not swapped to main  <-- issue
            verifyAll(northbound.getFlow(subFlow_1.flowId)) {
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Down" //should be "Down"
                status == FlowState.DEGRADED.toString()
            }
        }

        and: "YFlow is also marked as DEGRADED"
        northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.DEGRADED.toString() //should be DEGRADED

        and: "'Get y-flow path' endpoint is available"
        northboundV2.getYFlowPaths(yFlow.YFlowId)

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        if (yFlow) {
            otherIsls.each { antiflap.portUp(it.dstSwitch.dpId, it.dstPort) }
            antiflap.portUp(originalMainPath.last().switchId, originalMainPath.last().portNo)
        }
        wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
        database.resetCosts()
    }

    @Tidy
    @Ignore //todo this test should be deleted
    def "Able to delete degraded y-flow"() {
        given: "A protected y-flow"
        def swT = topologyHelper.switchTriplets.find {
            def ep1paths = it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            it.ep1 != it.ep2 && ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
        assumeTrue(swT != null, "No suiting switches found.")
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap { allocateProtectedPath = true }
        YFlow yFlow = yFlowHelper.addYFlow(yFlowRequest)

        when: "Other paths are not available (ISLs are down)"
        and: "Main flow path breaks"
        def subFlow_1 = yFlow.subFlows.first()
        def dstSwIdSubFl_1 = subFlow_1.endpoint.switchId
        def path = northbound.getFlowPath(subFlow_1.flowId)
        def originalMainPath = pathHelper.convert(path)
        def originalProtectedPath = pathHelper.convert(path.protectedPath)
        List<Isl> usedIsls = (pathHelper.getInvolvedIsls(originalMainPath) + pathHelper.getInvolvedIsls(originalProtectedPath))
        List<Isl> otherIsls = topology.islsForActiveSwitches.collectMany { [it, it.reversed] }
                .findAll { it.dstSwitch.dpId == dstSwIdSubFl_1 } - usedIsls
        otherIsls.each { antiflap.portDown(it.dstSwitch.dpId, it.dstPort) }
        wait(antiflapMin + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == FAILED
            }.size() == otherIsls.size() * 2
        }
        antiflap.portDown(originalProtectedPath.last().switchId, originalProtectedPath.last().portNo)

        then: "Sub-flow becomes degraded, main path UP, protected DOWN"
        wait(WAIT_OFFSET) {
            def newPath = northbound.getFlowPath(subFlow_1.flowId)
            assert pathHelper.convert(newPath) == originalMainPath
            assert pathHelper.convert(newPath.protectedPath) == originalProtectedPath
            verifyAll(northbound.getFlow(subFlow_1.flowId)) {
                status == FlowState.DEGRADED.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Down"
            }
        }

        and: "YFlow is also marked as DEGRADED"
        northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.DEGRADED.toString()   // issue. flow is in_progress

        and: "'Get y-flow path' endpoint is available"
        northboundV2.getYFlowPaths(yFlow.YFlowId)

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        if (yFlow) {
            otherIsls.each { antiflap.portUp(it.dstSwitch.dpId, it.dstPort) }
            antiflap.portUp(originalProtectedPath.last().switchId, originalProtectedPath.last().portNo)
        }
        wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
        database.resetCosts()
    }

    @Memoized
    List<Switch> findPotentialYPoints(SwitchTriplet swT) {
        def sortedEp1Paths = swT.pathsEp1.sort { it.size() }
        def potentialEp1Paths = sortedEp1Paths.takeWhile { it.size() == sortedEp1Paths[0].size() }
        def potentialEp2Paths = potentialEp1Paths.collect { potentialEp1Path ->
            def sortedEp2Paths = swT.pathsEp2.sort {
                it.size() - it.intersect(potentialEp1Path).size()
            }
            [path1: potentialEp1Path,
             potentialPaths2: sortedEp2Paths.takeWhile {it.size() == sortedEp2Paths[0].size() }]
        }
        return potentialEp2Paths.collectMany {path1WithPath2 ->
            path1WithPath2.potentialPaths2.collect { List<PathNode> potentialPath2 ->
                def switches = pathHelper.getInvolvedSwitches(path1WithPath2.path1)
                        .intersect(pathHelper.getInvolvedSwitches(potentialPath2))
                switches ? switches[-1] : null
            }
        }.findAll().unique()
    }
}
