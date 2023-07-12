package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.model.stats.FlowStats

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Tags(LOW_PRIORITY)
@Narrative("Verify that statistic is collected for different type of flow")
class FlowStatSpec extends HealthCheckSpecification {

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

    @Tidy
    def "System is able to collect stats after intentional swapping flow path to protected"() {
        given: "Two active neighboring switches with two diverse paths at least"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.dpId in traffGenSwitches && it.dst.dpId in traffGenSwitches &&
                    it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2
        } ?: assumeTrue(false, "No suiting switches found")

        and: "Flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)

        when: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flowHelperV2.toV1(flow),
                (int) flow.maximumBandwidth, 5).tap { udp = true }
        //generate two points of stat just to be sure that stat is not collected for protected path
        2.times { count ->
            exam.setResources(traffExam.startExam(exam))
            assert traffExam.waitExam(exam).hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "Stats collects stat for main path cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        and: "System collects stats for egress cookie of protected path with zero value"
        def protectedReverseCookie = flowInfo.protectedReversePath.cookie.value
        !stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), protectedReverseCookie).hasNonZeroValues()

        when: "Swap main and protected path"
        northbound.swapFlowPath(flow.flowId)
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
            def newFlowPathInfo = northbound.getFlowPath(flow.flowId)
            assert pathHelper.convert(newFlowPathInfo) == currentProtectedPath
            assert pathHelper.convert(newFlowPathInfo.protectedPath) == currentPath
        }

        and: "Generate traffic on the flow"
        def newProtectedReverseCookieStat
        //generate two points of stat to be sure that stat is not collected for a new protected path(after swapping)
        2.times { count ->
            exam.setResources(traffExam.startExam(exam))
            assert traffExam.waitExam(exam).hasTraffic()
            statsHelper."force kilda to collect stats"()
        }

        then: "System collects stats for previous egress cookie of protected path with non zero value"
        def newFlowStats = stats.of(flow.getFlowId())
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), protectedReverseCookie).hasNonZeroValues()

        and: "System doesn't collect stats anymore for previous ingress/egress cookie of main path"
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).getDataPoints().size() ==
                stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).getDataPoints().size()
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).getDataPoints().size() ==
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).getDataPoints().size()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    def "System collects stats when a protected flow was intentionally rerouted"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.src.dpId in traffGenSwitches && it.dst.dpId in traffGenSwitches &&
                    it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 3
        } ?: assumeTrue(false, "No suiting switches found")

        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        assert flowPathInfo.protectedPath
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)

        when: "Generate traffic on the given flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flowHelperV2.toV1(flow),
                (int) flow.maximumBandwidth, 3).tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats is not empty for main path cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        and: "Stats is empty for protected path egress cookie"
        def protectedReverseCookie = flowInfo.protectedReversePath.cookie.value
        !stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), protectedReverseCookie).hasNonZeroValues()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.paths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Init intentional reroute"
        def rerouteResponse = northbound.rerouteFlow(flow.flowId)
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }

        def flowPathInfoAfterRerouting = northbound.getFlowPath(flow.flowId)
        def newCurrentPath = pathHelper.convert(flowPathInfoAfterRerouting)
        newCurrentPath != currentPath
        newCurrentPath != currentProtectedPath

        and: "Generate traffic on the flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()


        then: "Stats is not empty for new main path cookies"
        def newFlowInfo = database.getFlow(flow.flowId)
        def newMainForwardCookie = newFlowInfo.forwardPath.cookie.value
        def newMainReverseCookie = newFlowInfo.reversePath.cookie.value
        def newFlowStats = flowStats.of(flow.getFlowId())
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), newMainForwardCookie).hasNonZeroValues()
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), newMainReverseCookie).hasNonZeroValues()

        and: "Stats is empty for a new protected path egress cookie"
        def newProtectedReverseCookie = newFlowInfo.protectedReversePath.cookie.value
        !newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), newProtectedReverseCookie).hasNonZeroValues()

        and: "Cleanup: revert system to original state"
        flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    @Tidy
    def "System collects stats when a protected flow was automatically rerouted"() {
        given: "Two active not neighboring switches with three not overlapping paths at least"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.src.dpId in traffGenSwitches && it.dst.dpId in traffGenSwitches &&
                    it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 3
        } ?: assumeTrue(false, "No suiting switches found")

        and: "A flow with protected path"
        def flow = flowHelper.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.id)
        assert flowPathInfo.protectedPath
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)

        when: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flow, (int) flow.maximumBandwidth, 3)
                .tap { udp = true}
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for main path cookies"
        def flowInfo = database.getFlow(flow.getId())
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        and: "System collects stats for egress cookie of protected path with zero value"
        def protectedReverseCookie = flowInfo.protectedReversePath.cookie.value
        !stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), protectedReverseCookie).hasNonZeroValues()

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = pathHelper.getInvolvedIsls(currentPath)[0]
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        def portIsDown = true
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.id).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) == currentProtectedPath
        }

        and: "Generate traffic on the flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for previous egress cookie of protected path with non zero value"
        def newFlowStats = flowStats.of(flow.getId())
        Wrappers.wait(statsRouterInterval) {
            flowStats.of(flow.getId()).get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), protectedReverseCookie).hasNonZeroValues()
        }

        and: "System doesn't collect stats for previous main path cookies due to main path is broken"
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).getDataPoints().size() ==
                stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).getDataPoints().size()
        newFlowStats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).getDataPoints().size() ==
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).getDataPoints().size()

        cleanup:
        flow && flowHelper.deleteFlow(flow.id)
        islToBreak && portIsDown && antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts(topology.isls)
    }

    @Tidy
    def "System collects stat when protected flow is DEGRADED"() {
        given: "Two active not neighboring switches with two not overlapping paths at least"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.src.dpId in traffGenSwitches && it.dst.dpId in traffGenSwitches &&
                    it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2
        } ?: assumeTrue(false, "No suiting switches found")

        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        switchPair.paths.findAll {
            it.first() != pathHelper.convert(flowPathInfo).first() &&
                    it.first() != pathHelper.convert(flowPathInfo.protectedPath).first()
        }.unique {
            it.first()
        }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        def srcPortIsDown = true
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Break ISL on a protected path (bring port down) for changing the flow state to DEGRADED"
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        antiflap.portDown(protectedIsls[0].dstSwitch.dpId, protectedIsls[0].dstPort)
        def dstPortIsDown = true
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northboundV2.getFlow(flow.flowId)) {
                status == "Degraded"
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
            }
        }

        and: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flowHelperV2.toV1(flow),
                (int) flow.maximumBandwidth, 3).tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for main path cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        cleanup: "Restore topology, delete flows and reset costs"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        protectedIsls && srcPortIsDown && antiflap.portUp(protectedIsls[0].srcSwitch.dpId, protectedIsls[0].srcPort)
        protectedIsls && dstPortIsDown && antiflap.portUp(protectedIsls[0].dstSwitch.dpId, protectedIsls[0].dstPort)
        broughtDownPorts && broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Tags([SMOKE_SWITCHES])
    def "System collects stats when flow is pinned and unmetered"() {
        given: "Two active not neighboring switches"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.src.dpId in traffGenSwitches && it.dst.dpId in traffGenSwitches
        } ?: assumeTrue(false, "No suiting switches found")

        and: "An unmetered flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = 0
        flow.ignoreBandwidth = true
        flow.pinned = true
        flow.periodicPings = true
        flowHelperV2.addFlow(flow)

        when: "Generate traffic on the given flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flowHelperV2.toV1(flow),
                (int) flow.maximumBandwidth, 3).tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for egress/ingress cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System is able to collect stats after partial updating(port) on a flow endpoint"() {
        given: "Two active neighboring switches connected to the traffgens"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.switchPairs.find {
            [it.src, it.dst].every { it.dpId in traffGenSwitches }
        } ?: assumeTrue(false, "No suiting switches found")

        and: "A flow with updated port on src endpoint via partial update"
        def traffgenPortOnSrcSw = topology.activeTraffGens.find { it.switchConnected ==  switchPair.src}.switchPort
        def srcFlowPort = (topology.getAllowedPortsForSwitch(
                topology.find(switchPair.src.dpId)) - traffgenPortOnSrcSw).last()

        def flow = flowHelperV2.randomFlow(switchPair).tap { it.source.portNumber = srcFlowPort }
        flowHelperV2.addFlow(flow)

        flowHelperV2.partialUpdate(flow.flowId, new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {portNumber = traffgenPortOnSrcSw }
        })

        when: "Generate traffic on the flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(northbound.getFlow(flow.flowId),
                (int) flow.maximumBandwidth, 5).tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for ingress/egress cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System is able to collect stats after partial updating(vlan) on a flow endpoint"() {
        given: "Two active neighboring switches connected to the traffgens"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.switchPairs.find {
            [it.src, it.dst].every { it.dpId in traffGenSwitches }
        } ?: assumeTrue(false, "No suiting switches found")

        and: "A flow with updated vlan on src endpoint via partial update"
        def traffgenPortOnSrcSw = topology.activeTraffGens.find { it.switchConnected ==  switchPair.src}.switchPort

        def flow = flowHelperV2.randomFlow(switchPair).tap { it.source.portNumber = traffgenPortOnSrcSw;  it.source.vlanId = 100}
        flowHelperV2.addFlow(flow)

        flowHelperV2.partialUpdate(flow.flowId, new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap { vlanId = vlanId ?: 100 + 1 }
        })

        when: "Generate traffic on the flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        Exam exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(northbound.getFlow(flow.flowId),
                (int) flow.maximumBandwidth, 5).tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for ingress/egress cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System is able to collect stats after partial updating(inner vlan) on a flow endpoint"() {
        given: "Two active neighboring switches connected to the traffgens"
        def traffGenSwitches = topology.activeTraffGens*.switchConnected*.dpId
        def switchPair = topologyHelper.switchPairs.find {
            [it.src, it.dst].every { it.dpId in traffGenSwitches } &&
                    switchHelper.getCachedSwProps(it.src.dpId).multiTable
        } ?: assumeTrue(false, "No suiting switches found")

        and: "A flow with updated inner vlan on src endpoint via partial update"
        def flow = flowHelperV2.randomFlow(switchPair, true)
        flowHelperV2.addFlow(flow)

        flowHelperV2.partialUpdate(flow.flowId, new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap { innerVlanId = flow.source.vlanId - 1 }
        })

        when: "Generate traffic on the flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(northbound.getFlow(flow.flowId),
                (int) flow.maximumBandwidth, 5)
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "System collects stats for ingress/egress cookies"
        def flowInfo = database.getFlow(flow.flowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        def stats = flowStats.of(flow.getFlowId())
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainForwardCookie).hasNonZeroValues()
        stats.get(FLOW_RAW_BYTES, switchPair.getSrc().getDpId(), mainReverseCookie).hasNonZeroValues()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }
}
