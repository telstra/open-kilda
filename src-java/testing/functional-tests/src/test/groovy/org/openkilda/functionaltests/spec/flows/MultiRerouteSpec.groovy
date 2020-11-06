package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.tools.SoftAssertions

import spock.lang.Ignore

import java.util.concurrent.TimeUnit

class MultiRerouteSpec extends HealthCheckSpecification {

    @Tidy
    @Ignore("scenario should be updated with respect to #tbd")
    def "Simultaneous reroute of multiple flows should not oversubscribe any ISLs"() {
        given: "Two flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 2 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowRequestV2> flows = []
        30.times {
            def flow = flowHelperV2.randomFlow(switchPair, false, flows)
            flow.maximumBandwidth = 10000
            flowHelperV2.addFlow(flow)
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].flowId))
        //ensure all flows are on the same path
        flows[1..-1].each {
            assert pathHelper.convert(northbound.getFlowPath(it.flowId)) == currentPath
        }

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Make preferable path's ISL to have bandwidth to host only half of the rerouting flows"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(newPath)
        def notNewIsls = switchPair.paths.findAll { it != newPath }.collectMany {
            def isls = pathHelper.getInvolvedIsls(it)
            [isls, isls*.reversed]
        }.unique(false)
        def thinIsl = newIsls.find { !notNewIsls.contains(it) }
        def halfOfFlows = flows[0..flows.size() / 2 - 1]
        long newBw = halfOfFlows.sum { it.maximumBandwidth }
        [thinIsl, thinIsl.reversed].each { database.updateIslMaxBandwidth(it, newBw) }
        [thinIsl, thinIsl.reversed].each { database.updateIslAvailableBandwidth(it, newBw) }

        and: "Init simultaneous reroute of all flows by bringing current path's ISL down"
        def notCurrentIsls = switchPair.paths.findAll { it != currentPath }.collectMany {
            def isls = pathHelper.getInvolvedIsls(it)
            [isls, isls*.reversed]
        }.unique()
        def islToBreak = currentIsls.find { !notCurrentIsls.contains(it) }
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)

        then: "Half of the flows are hosted on the preferable path"
        def flowsOnPrefPath
        wait(WAIT_OFFSET * 2) {
            def assertions = new SoftAssertions()
            flowsOnPrefPath = flows.findAll {
                pathHelper.convert(northbound.getFlowPath(it.flowId)) == newPath
            }
            flowsOnPrefPath.each { flow ->
                assertions.checkSucceeds { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }
            }
            assertions.checkSucceeds { assert flowsOnPrefPath.size() == halfOfFlows.size() }
            assertions.verify()
        }

        and: "Rest of the flows are hosted on another alternative path"
        def restFlows = flows.findAll { !flowsOnPrefPath*.flowId.contains(it.flowId) }
        def restFlowsPath = pathHelper.convert(northbound.getFlowPath(restFlows[0].flowId))
        restFlowsPath != newPath
        wait(WAIT_OFFSET) {
            def assertions = new SoftAssertions()
            restFlows[1..-1].each { flow ->
                assertions.checkSucceeds { assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == restFlowsPath }
                assertions.checkSucceeds { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }
            }
            assertions.verify()
        }

        and: "None ISLs are oversubscribed"
        northbound.getAllLinks().each { assert it.availableBandwidth >= 0 }

        cleanup: "revert system to original state"
        flows.each { flowHelperV2.deleteFlow(it.flowId) }
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        [thinIsl, thinIsl.reversed].each { database.resetIslBandwidth(it) }
        database.resetCosts()
        wait(WAIT_OFFSET + discoveryInterval) {
            assert northbound.getLink(islToBreak).state == IslChangeType.DISCOVERED
        }
    }

    def "multiReroute for checking perf"() {
        given: "Switch pair with several available paths, one path should have a transit link \
        (we will use for a flow2 and break this link) and at least 1 path must remain safe"
        def allPathsFlow1, allPathCandidatesFlow1, allPathsFlow2
        def transitLinks
        def swPair2
        def flow2Path, flow1Path, flow1BackupPath
        def swPair1 = topologyHelper.getAllNeighboringSwitchPairs().find { swPFlow1 ->
                    allPathsFlow1 = swPFlow1.paths
                    flow1BackupPath = allPathsFlow1.min { it.size() }
                    allPathCandidatesFlow1 = allPathsFlow1.findAll { pathHelper.getInvolvedSwitches(it).size() >= 4 }
                    transitLinks = allPathCandidatesFlow1.collectMany { (pathHelper.getInvolvedIsls(it)[1..-2]) }.unique()
                    swPair2 = topologyHelper.getAllNotNeighboringSwitchPairs().find { swPFlow2 ->
                        allPathsFlow2 = swPFlow2.paths
                        def srcSwIdToMatch = swPFlow1.src.dpId
                        def dstSwIdToAvoid = swPFlow1.dst.dpId
                        def transitIslsFlow2
                        flow2Path = allPathsFlow2.find { path ->
                            if (pathHelper.getInvolvedSwitches(path).size() > 3) {
                                transitIslsFlow2 = pathHelper.getInvolvedIsls(path)[1..-2]
                                swPFlow2.src.dpId == srcSwIdToMatch && swPFlow2.dst.dpId != dstSwIdToAvoid && transitIslsFlow2.any { it in transitLinks }
                            }
                        }
                        flow1Path = allPathCandidatesFlow1.find { pathHelper.getInvolvedIsls(it)[1..-2].any {it in transitIslsFlow2} }
                        flow2Path && flow1Path
                    }
                }

        def involvedIslsInMainPaths = [flow1Path, flow2Path, flow1BackupPath].collectMany { pathHelper.getInvolvedIsls(it)}
                .collectMany {[it, it.reversed] }.unique()


        and: "All alternative path are unavailable"
        def islsToBreak = (allPathsFlow1 + allPathsFlow2).collectMany { pathHelper.getInvolvedIsls(it) }
                .collectMany { [it, it.reversed] }.unique()
                .findAll { !involvedIslsInMainPaths.contains(it) }.unique { [it, it.reversed].sort() }
        withPool { islsToBreak.eachParallel { Isl isl -> antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort) } }
        wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll { it.state == FAILED }.size() == islsToBreak.size() * 2
        }

        and: "flow1MainPath is more preferable than flow1BackupPath"
        pathHelper.makePathNotPreferable(flow1BackupPath)

        and: "Flows on the given switch pairs and paths"
        List<FlowRequestV2> flows1 = []
        20.times {
            def flow = flowHelperV2.randomFlow(swPair1, false, flows1)
            flowHelperV2.addFlow(flow)
            flows1 << flow
        }
        List<FlowRequestV2> flows2 = []
        20.times {
            def flow = flowHelperV2.randomFlow(swPair2, false, flows1 + flows2)
            flowHelperV2.addFlow(flow)
            flows2 << flow
        }
        flows1.each {
            assert flow1Path == PathHelper.convert(northbound.getFlowPath(it.flowId))
        }
        flows2.each {
            assert flow2Path == PathHelper.convert(northbound.getFlowPath(it.flowId))
        }

        when: "Init auto reroute, break all transit ISLs for flow1"
        def transitIslsToFail = pathHelper.getInvolvedIsls(flow1Path)[1..-1]
        withPool { transitIslsToFail.eachParallel { Isl isl -> antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort) } }
        wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == FAILED
            }.size() == (islsToBreak.size() + transitIslsToFail.size()) * 2
        }

        then: "Flows on flow1MainPath are rerouted to flow1BackupPath"
        wait(WAIT_OFFSET * 2) {
        flows1.each {
                assert flow1BackupPath == PathHelper.convert(northbound.getFlowPath(it.flowId))
                assert northbound.getFlowStatus(it.flowId).status == FlowState.UP
            }
        }

        and: "Flows on flow2Path are NOT rerouted"
        wait(rerouteDelay + WAIT_OFFSET) {
            flows2.each {
                assert northbound.getFlowStatus(it.flowId).status == FlowState.DOWN
                assert northbound.getFlowHistory(it.flowId).find {
                    it.action == REROUTE_ACTION && it.taskId =~ (/.+ : retry #1 ignore_bw true/)
                }?.payload?.last()?.action == REROUTE_FAIL
            }
        }

        cleanup:
        flows1 && flows1.each { flowHelperV2.deleteFlow(it.flowId) }
        flows2 && flows2.each { flowHelperV2.deleteFlow(it.flowId) }
        islsToBreak && withPool { islsToBreak.eachParallel { Isl isl -> antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort) } }
        transitIslsToFail && withPool { transitIslsToFail.eachParallel { Isl isl -> antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort) }}
        wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll { it.state == FAILED }.empty
        }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
