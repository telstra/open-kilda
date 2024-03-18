package org.openkilda.functionaltests.spec.flows.yflows

import org.openkilda.functionaltests.helpers.Wrappers

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

import org.openkilda.functionaltests.error.yflow.YFlowRerouteExpectedError
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.SwitchTriplet

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_SUCCESS_Y
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v1.switches.PortDto
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
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
@Narrative("Verify reroute operations on y-flows.")
class YFlowRerouteSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired @Shared
    FlowStats flowStats

    @Tags([TOPOLOGY_DEPENDENT, ISL_RECOVER_ON_FAIL])
    def "Valid y-flow can be rerouted"() {
        given: "A qinq y-flow"
        def swT = topologyHelper.switchTriplets.find { it ->
            def yPoints = topologyHelper.findPotentialYPoints(it)
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every { it.size() > 1 } &&
                    it.ep1 != it.ep2 && yPoints.size() == 1 && yPoints[0] != it.shared &&
                    !it.shared.wb5164 && !it.ep1.wb5164 && !it.ep2.wb5164
        }
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap {
            it.subFlows[0].sharedEndpoint.vlanId = 123
            it.subFlows[1].sharedEndpoint.vlanId = 124
            it.subFlows[0].sharedEndpoint.innerVlanId = 111
            it.subFlows[1].sharedEndpoint.innerVlanId = 111
            it.subFlows[0].endpoint.vlanId = 222
            it.subFlows[1].endpoint.vlanId = 222
            it.subFlows[0].endpoint.innerVlanId = 333
            it.subFlows[1].endpoint.innerVlanId = 444
        }
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        def paths = northboundV2.getYFlowPaths(yFlow.YFlowId)
        def islToFail = pathHelper.getInvolvedIsls(PathHelper.convert(paths.subFlowPaths[0].forward)).first()

        when: "Fail a flow ISL (bring switch port down)"
        islHelper.breakIsl(islToFail)

        then: "The flow was rerouted after reroute delay"
        and: "History has relevant entries about y-flow reroute"
        wait(FLOW_CRUD_TIMEOUT) {
            assert flowHelper.getLatestHistoryEntry(yFlow.getYFlowId()).payload.last().action == REROUTE_SUCCESS_Y
        }
        yFlow.subFlows.each { sf ->
            assert flowHelper.getLatestHistoryEntry(sf.flowId).payload.last().action == REROUTE_SUCCESS
        }
        wait(rerouteDelay + WAIT_OFFSET) {
            yFlow = northboundV2.getYFlow(yFlow.YFlowId)
            assert yFlow.status == FlowState.UP.toString()
            assert northboundV2.getYFlowPaths(yFlow.YFlowId) != paths
        }

        and: "Y-flow passes flow validation"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected

        and: "Both sub-flows pass flow validation"
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).every { it.asExpected }
        }

        and: "All involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(pathHelper.getInvolvedYSwitches(paths)*.getDpId()).isEmpty()

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildYFlowExam(yFlow, yFlow.maximumBandwidth, 10)
        List<ExamReport> examReports = withPool {
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


        and: "Subflows stats are available (flow.raw.bytes)"
        def subflow = yFlow.getSubFlows().shuffled().first()
        def subflowId = subflow.getFlowId()
        def flowInfo = database.getFlow(subflowId)
        def mainForwardCookie = flowInfo.forwardPath.cookie.value
        def mainReverseCookie = flowInfo.reversePath.cookie.value
        wait(statsRouterRequestInterval + WAIT_OFFSET) {
            def stats = flowStats.of(subflowId)
            assert stats.get(FLOW_RAW_BYTES, subflow.getEndpoint().getSwitchId(), mainForwardCookie).hasNonZeroValues()
            assert stats.get(FLOW_RAW_BYTES, yFlow.getSharedEndpoint().getSwitchId(), mainReverseCookie).hasNonZeroValues()
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        islHelper.restoreIsl(islToFail)
        database.resetCosts(topology.isls)
    }

    @Tags([LOW_PRIORITY])
    def "Y-Flow reroute has not been executed when both sub-flows are on the best path"() {
        given: "Y-Flow has been created successfully"
        def swT = topologyHelper.switchTriplets.findAll { SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it) }.first()
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        def yFlowPathBeforeReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)

        when: "Y-Flow reroute has been called"
        northboundV2.rerouteYFlow(yFlow.YFlowId)

        then: "The appropriate error has been returned"
        def actualException = thrown(HttpClientErrorException)
        new YFlowRerouteExpectedError(~/Reroute is unsuccessful. Couldn't find new path\(s\)/).matches(actualException)

        and: "Y-Flow path has not been changed"
        def yFlowPathAfterReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)
        verifyAll {
            northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
            yFlowPathAfterReroute == yFlowPathBeforeReroute
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tags([LOW_PRIORITY])
    def "Y-Flow reroute has been executed when more preferable path is available for both sub-flows (shared path cost was changed)" () {
        given: "The appropriate switches have been collected"
        //y-flow with shared path is created when shared_ep+ep1->neighbour && ep1+ep2->neighbour && shared_ep+ep2->not neighbour
        def pairSharedEpAndEp1 = switchPairs.all().neighbouring().first()
        def sharedEpNeighbouringSwitches = switchPairs.all().neighbouring().includeSwitch(pairSharedEpAndEp1.src).collectSwitches()
        def pairEp1AndEp2 = switchPairs.all().neighbouring().excludePairs([pairSharedEpAndEp1])
                .includeSwitch(pairSharedEpAndEp1.dst).excludeSwitches(sharedEpNeighbouringSwitches).first()
        def swT = new SwitchTriplet(shared: pairSharedEpAndEp1.src, ep1: pairEp1AndEp2.src, ep2: pairEp1AndEp2.dst)

        and: "The ISLs cost between switches has been changed to make preferable path"
        List<Isl> directSwTripletIsls = (pairSharedEpAndEp1.paths + pairEp1AndEp2.paths).findAll { it.size() == 2 }
                .collectMany { pathHelper.getInvolvedIsls(it) }

        pathHelper.updateIslsCost(directSwTripletIsls, 0)

        and: "Y-Flow with shared path has been created successfully"
        def yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(swT, false))
        def yFlowPathBeforeReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)

        and: "Shared ISLs cost has been changed to provide on-demand Y-Flow reroute"
        def sharedPathIslBeforeReroute = new Path(yFlowPathBeforeReroute.sharedPath.forward + yFlowPathBeforeReroute.sharedPath.reverse, topology).getInvolvedIsls()
        pathHelper.updateIslsCost(sharedPathIslBeforeReroute, 80000)

        when: "Y-Flow reroute has been called"
        YFlowRerouteResult rerouteDetails = northboundV2.rerouteYFlow(yFlow.YFlowId)

        then: "Y-Flow reroute has been executed successfully"
        verifyAll {
            rerouteDetails.rerouted
            rerouteDetails.subFlowPaths.size() == 2
        }

        and: "Both sub-flows paths have been changed"
        wait(FLOW_CRUD_TIMEOUT) {
            assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
        }

        def yFlowPathAfterReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)
        verifyAll {
            yFlowPathAfterReroute.subFlowPaths.first().forward != yFlowPathBeforeReroute.subFlowPaths.first().forward
            yFlowPathAfterReroute.subFlowPaths.first().reverse != yFlowPathBeforeReroute.subFlowPaths.first().reverse
            yFlowPathAfterReroute.subFlowPaths.last().forward != yFlowPathBeforeReroute.subFlowPaths.last().forward
            yFlowPathAfterReroute.subFlowPaths.last().reverse != yFlowPathBeforeReroute.subFlowPaths.last().reverse
        }

        cleanup:
        northbound.deleteLinkProps(northbound.getLinkProps(sharedPathIslBeforeReroute + directSwTripletIsls))
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tags([LOW_PRIORITY])
    def "Y-Flow reroute has been executed when more preferable path is available for one of the sub-flows" () {
        given: "The appropriate switches have been collected"
        //y-flow with shared path is created when shared_ep+ep1->neighbour && ep1+ep2->neighbour && shared_ep+ep2->not neighbour
        def pairSharedEpAndEp1 = switchPairs.all().neighbouring().first()
        def sharedEpNeighbouringSwitches = switchPairs.all().neighbouring().includeSwitch(pairSharedEpAndEp1.src).collectSwitches()
        def pairEp1AndEp2 = switchPairs.all().neighbouring().excludePairs([pairSharedEpAndEp1])
                .includeSwitch(pairSharedEpAndEp1.dst).excludeSwitches(sharedEpNeighbouringSwitches).first()
        def swT = new SwitchTriplet(shared: pairSharedEpAndEp1.src, ep1: pairEp1AndEp2.src, ep2: pairEp1AndEp2.dst)

        and: "The ISLs cost between switches has been changed to make preferable path"
        List<Isl> directSwTripletIsls = (pairSharedEpAndEp1.paths + pairEp1AndEp2.paths).findAll { it.size() == 2 }
                .collectMany { pathHelper.getInvolvedIsls(it) }
        pathHelper.updateIslsCost(directSwTripletIsls, 0)

        and: "Y-Flow with shared path has been created successfully"
        def yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(swT, false))
        def yFlowPathBeforeReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)

        and: "The required ISLs cost has been updated to make manual reroute available"
        def islsSubFlow1 = new Path(yFlowPathBeforeReroute.subFlowPaths.first().forward + yFlowPathBeforeReroute.subFlowPaths.first().reverse, topology).getInvolvedIsls()
        def islsSubFlow2 = new Path(yFlowPathBeforeReroute.subFlowPaths.last().forward + yFlowPathBeforeReroute.subFlowPaths.last().reverse, topology).getInvolvedIsls()
        assert islsSubFlow1 != islsSubFlow2, "Y-Flow path doesn't allow us to the check this case as subFlows have the same ISLs"

        def islsToModify
        String subFlowId
        if (islsSubFlow1.size() > islsSubFlow2.size()) {
            islsToModify = islsSubFlow1.findAll { !(it in islsSubFlow2) }
            subFlowId = yFlowPathBeforeReroute.subFlowPaths.first().flowId
        } else {
            islsToModify = islsSubFlow2.findAll { !(it in islsSubFlow1) }
            subFlowId = yFlowPathBeforeReroute.subFlowPaths.last().flowId
        }
        pathHelper.updateIslsCost(islsToModify, 80000)

        when: "Y-Flow reroute has been called"
        YFlowRerouteResult rerouteDetails = northboundV2.rerouteYFlow(yFlow.YFlowId)

        then: "Y-Flow has been rerouted successfully"
        verifyAll {
            rerouteDetails.rerouted
            rerouteDetails.subFlowPaths.size() == 2
        }

        and: "The appropriate flow has been rerouted"
        wait(FLOW_CRUD_TIMEOUT) {
            assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
        }
        def yFlowPathAfterReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)
        verifyAll {
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId == subFlowId }.forward != yFlowPathBeforeReroute.subFlowPaths.find { it.flowId == subFlowId }.forward
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId == subFlowId }.reverse != yFlowPathBeforeReroute.subFlowPaths.find { it.flowId == subFlowId }.reverse
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId != subFlowId }.forward == yFlowPathBeforeReroute.subFlowPaths.find { it.flowId != subFlowId }.forward
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId != subFlowId }.reverse == yFlowPathBeforeReroute.subFlowPaths.find { it.flowId != subFlowId }.reverse
        }

        cleanup:
        northbound.deleteLinkProps(northbound.getLinkProps(islsToModify + directSwTripletIsls))
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "Y-Flow reroute has not been executed when one sub-flow is on the best path and there is no alternative path for another sub-flow due to the down ISLs" () {
        given: "Y-Flow has been created successfully"
        def swT = topologyHelper.switchTriplets.findAll{ SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it)}.first()
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        def yFlowPathBeforeReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)

        and: "Sub-flows not intersected ISLs have been collected"
        def islsSubFlow1 = new Path(yFlowPathBeforeReroute.subFlowPaths.first().forward, topology).getInvolvedIsls()
        def islsSubFlow2 = new Path(yFlowPathBeforeReroute.subFlowPaths.last().forward, topology).getInvolvedIsls()
        def notIntersectedIsls = islsSubFlow1.size() > islsSubFlow2.size() ?
                islsSubFlow1.findAll { !(it in islsSubFlow2) } : islsSubFlow2.findAll { !(it in islsSubFlow1) }

        and: "Switch off all ISLs on the terminal switch"
        Switch terminalSwitch = notIntersectedIsls.last().dstSwitch
        def broughtDownIsls = topology.getRelatedIsls(terminalSwitch)
        islHelper.breakIsls(broughtDownIsls)
        wait(FLOW_CRUD_TIMEOUT) {
            northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.DEGRADED.getState()
        }

        when: "Y-Flow reroute has been called"
        northboundV2.rerouteYFlow(yFlow.YFlowId)

        then: "The appropriate error has been returned"
        def actualException = thrown(HttpClientErrorException)
        new YFlowRerouteExpectedError(~/Not enough bandwidth or no path found. Switch ${terminalSwitch.dpId} doesn't have links with enough bandwidth/).matches(actualException)

        and: "Y-Flow path has not been changed"
        def yFlowPathAfterReroute = northboundV2.getYFlowPaths(yFlow.YFlowId)
        verifyAll {
            northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.DEGRADED.toString()
            yFlowPathAfterReroute == yFlowPathBeforeReroute
        }

        cleanup:
        islHelper.restoreIsls(broughtDownIsls)
        wait(WAIT_OFFSET) {
            northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
        }
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }
}
