package org.openkilda.functionaltests.spec.stats

import static org.openkilda.functionaltests.model.stats.Direction.*
import static org.openkilda.messaging.payload.flow.FlowState.*

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import jakarta.inject.Provider

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Tags([LOW_PRIORITY])
@Narrative("Verify that statistic is collected for different type of flow")
class FlowStatSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Shared
    Integer statsRouterInterval

    @Autowired @Shared
    FlowStats flowStats

    def setupSpec() {
        /*it can't be initialized properly in Shared scope, that's why setupSpec is used.
        statsRouterRequestInterval = 60, this test often fails on jenkins with this value
        the statsrouter.request.interval is increased up to 120 */
        statsRouterInterval = statsRouterRequestInterval * 2
    }

    def "System is able to collect stats after intentional swapping flow path to protected"() {
        given: "Two active neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "Flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        def mainPath = flowPathInfo.getPathNodes(FORWARD, false)
        def protectedPath = flowPathInfo.getPathNodes(FORWARD, true)

        when: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward.tap { udp = true }
        //generate two points of stat just to be sure that stat is not collected for protected path
        2.times { count ->
            exam.setResources(traffExam.startExam(exam))
            assert traffExam.waitExam(exam).hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "Stats collects stat for main path cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.flowId)
        stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()

        and: "System collects stats for egress cookie of protected path with zero value"
        def protectedReverseCookie = flowInfo.protectedReversePath.cookie.value
        !stats.get(FLOW_RAW_BYTES, srcSwitchId, protectedReverseCookie).hasNonZeroValues()

        when: "Swap main and protected path"
        flow.swapFlowPath()
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == UP
            def newFlowPathInfo = flow.retrieveAllEntityPaths()
            assert newFlowPathInfo.getPathNodes(FORWARD, false) == protectedPath
            assert newFlowPathInfo.getPathNodes(FORWARD, true) == mainPath
        }

        and: "Wait till stats from old main path are collected"
        Wrappers.wait(statsRouterRequestInterval, 3) {
            def oldStats = stats
            statsHelper."force kilda to collect stats"()
            stats = flowStats.of(flow.flowId)

            assert oldStats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).getNewestTimeStamp() ==
                    stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).getNewestTimeStamp()
            assert oldStats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).getNewestTimeStamp() ==
                    stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).getNewestTimeStamp()
        }

        and: "Generate traffic on the flow"
        //generate two points of stat to be sure that stat is not collected for a new protected path(after swapping)
        2.times { count ->
            exam.setResources(traffExam.startExam(exam))
            assert traffExam.waitExam(exam).hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "System collects stats for previous egress cookie of protected path with non zero value"
        def newFlowStats = stats.of(flow.flowId)
        newFlowStats.get(FLOW_RAW_BYTES, srcSwitchId, protectedReverseCookie).hasNonZeroValues()
    }

    def "System collects stats when a protected flow was intentionally rerouted"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withAtLeastNNonOverlappingPaths(3)
                .random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()
        def mainPathIsls = flowPathInfo.getMainPathInvolvedIsls()
        def protectedPathIsls = flowPathInfo.getProtectedPathInvolvedIsls()

        when: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 3).forward.tap{ udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats is not empty for main path cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats
        Wrappers.wait(statsRouterInterval) {
            stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()
        }

        and: "Stats is empty for protected path egress cookie"
        def protectedReverseCookie = flowInfo.protectedReversePath.cookie.value
        !stats.get(FLOW_RAW_BYTES, srcSwitchId, protectedReverseCookie).hasNonZeroValues()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { it != mainPathIsls && it != protectedPathIsls }
        alternativePaths.each { islHelper.makePathIslsMorePreferable(it, mainPathIsls) }
        alternativePaths.each { islHelper.makePathIslsMorePreferable(it, protectedPathIsls) }

        and: "Init intentional reroute"
        def rerouteResponse = flow.reroute()
        assert rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == UP }

        def flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()
        def newMainPath = flowPathInfoAfterRerouting.getMainPathInvolvedIsls()
        newMainPath != mainPathIsls
        newMainPath != protectedPathIsls

        and: "Generate traffic on the flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()


        then: "Stats is not empty for new main path cookies"
        def newFlowInfo = flow.retrieveDetailsFromDB()
        def newMainForwardCookie = newFlowInfo.forwardPath.cookie.value
        def newMainReverseCookie = newFlowInfo.reversePath.cookie.value
        def newFlowStats
        Wrappers.wait(statsRouterInterval) {
            newFlowStats = flowStats.of(flow.flowId)
            newFlowStats.get(FLOW_RAW_BYTES, srcSwitchId, newMainForwardCookie).hasNonZeroValues()
            newFlowStats.get(FLOW_RAW_BYTES, srcSwitchId, newMainReverseCookie).hasNonZeroValues()
        }

        and: "Stats is empty for a new protected path egress cookie"
        def newProtectedReverseCookie = newFlowInfo.protectedReversePath.cookie.value
        !newFlowStats.get(FLOW_RAW_BYTES, srcSwitchId, newProtectedReverseCookie).hasNonZeroValues()
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System collects stats when a protected flow was automatically rerouted"() {
        given: "Two active not neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withAtLeastNNonOverlappingPaths(3)
                .random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build()
                .createV1()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()
        def protectedPath = flowPathInfo.getPathNodes(FORWARD, true)

        when: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 3).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for main path cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats
        Wrappers.wait(statsRouterInterval) {
            stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()
        }

        and: "System collects stats for egress cookie of protected path with zero value"
        def protectedReverseCookie = flowInfo.protectedReversePath.cookie.value
        !stats.get(FLOW_RAW_BYTES, srcSwitchId, protectedReverseCookie).hasNonZeroValues()

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = flowPathInfo.flowPath.path.forward.getInvolvedIsls().first()
        islHelper.breakIsl(islToBreak)
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == UP
            assert flow.retrieveAllEntityPaths().getPathNodes(FORWARD, false) == protectedPath
        }
        def timeAfterSwap = new Date().getTime()

        and: "Generate traffic on the flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for previous egress cookie of protected path with non zero value"
        def newFlowStats = flowStats.of(flow.flowId)
        Wrappers.wait(statsRouterInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitchId, protectedReverseCookie).hasNonZeroValues()
        }

        and: "System doesn't collect stats for previous main path cookies due to main path is broken"
        !newFlowStats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValuesAfter(timeAfterSwap)
        !newFlowStats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValuesAfter(timeAfterSwap)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System collects stat when protected flow is DEGRADED"() {
        given: "Two active not neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withAtLeastNNonOverlappingPaths(2)
                .random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build()
                .create()

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def mainPathIsls = flowPathInfo.flowPath.path.forward.getInvolvedIsls()
        def protectedPathIsls = flowPathInfo.flowPath.protectedPath.forward.getInvolvedIsls()
        def altIsls = topology.getRelatedIsls(switchPair.src) - mainPathIsls - protectedPathIsls.first()
        islHelper.breakIsls(altIsls)

        when: "Break ISL on a protected path (bring port down) for changing the flow state to DEGRADED"
        islHelper.breakIsl(protectedPathIsls.first())
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveDetails()) {
                status == DEGRADED
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
            }
        }

        and: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 3).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for main path cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        Wrappers.wait(statsRouterInterval) {
            def stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()
        }
    }

    @Tags([SMOKE_SWITCHES])
    def "System collects stats when flow is pinned and unmetered"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().withTraffgensOnBothEnds().random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "An unmetered flow"
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(0)
                .withIgnoreBandwidth(true)
                .withPinned(true)
                .withPeriodicPing(true).build()
                .create()

        when: "Generate traffic on the given flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 3).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for egress/ingress cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        Wrappers.wait(statsRouterInterval) {
            def stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()
        }
    }

    def "System is able to collect stats after partial updating(port) on a flow endpoint"() {
        given: "Two active neighboring switches connected to the traffgens"
        def switchPair = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "A flow with updated port on src endpoint via partial update"
        def traffgenPortOnSrcSw = topology.activeTraffGens.find { it.switchConnected ==  switchPair.src}.switchPort
        def srcFlowPort = (topology.getAllowedPortsForSwitch(
                topology.find(switchPair.src.dpId)) - traffgenPortOnSrcSw).last()

        def flow = flowFactory.getBuilder(switchPair).withSourcePort(srcFlowPort).build()
                .create()

        flow = flow.partialUpdate(new FlowPatchV2().tap { source = new FlowPatchEndpoint().tap { portNumber = traffgenPortOnSrcSw } })

        when: "Generate traffic on the flow"
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for ingress/egress cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        Wrappers.wait(statsRouterInterval) {
            def stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()
        }
    }

    def "System is able to collect stats after partial updating(vlan) on a flow endpoint"() {
        given: "Two active neighboring switches connected to the traffgens"
        def switchPair = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()
        def srcSwitchId = switchPair.getSrc().getDpId()

        and: "A flow with updated vlan on src endpoint via partial update"
        def traffgenPortOnSrcSw = topology.activeTraffGens.find { it.switchConnected ==  switchPair.src}.switchPort

        def flow = flowFactory.getBuilder(switchPair)
                .withSourcePort(traffgenPortOnSrcSw)
                .withSourceVlan(100).build()
                .create()

        flow = flow.partialUpdate(new FlowPatchV2().tap { source = new FlowPatchEndpoint().tap { vlanId = vlanId ?: 100 + 1 } })

        when: "Generate traffic on the flow"
        def traffExam = traffExamProvider.get()
        Exam exam =flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for ingress/egress cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        Wrappers.wait(statsRouterInterval) {
            def stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, srcSwitchId, mainReverseCookie).hasNonZeroValues()
        }
    }

    def "System is able to collect stats after partial updating(inner vlan) on a flow endpoint"() {
        given: "Two active neighboring switches connected to the traffgens"
        def switchPair = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()


        and: "A flow with updated inner vlan on src endpoint via partial update"
        def flow = flowFactory.getRandom(switchPair, true)

        flow = flow.partialUpdate( new FlowPatchV2().tap { source = new FlowPatchEndpoint().tap { innerVlanId = flow.source.vlanId - 1 } })

        when: "Generate traffic on the flow"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for ingress/egress cookies"
        def flowInfo = flow.retrieveDetailsFromDB()
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        Wrappers.wait(statsRouterInterval) {
            def stats = flowStats.of(flow.flowId)
            stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
            stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()
        }
    }
}
