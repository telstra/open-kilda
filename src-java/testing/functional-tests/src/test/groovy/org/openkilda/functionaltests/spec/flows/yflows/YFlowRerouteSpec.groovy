package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.yflow.YFlowRerouteExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.YFlowActionType
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
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
@Narrative("Verify reroute operations on y-flows.")
class YFlowRerouteSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired @Shared
    FlowStats flowStats

    @Tags([TOPOLOGY_DEPENDENT, ISL_RECOVER_ON_FAIL])
    def "Valid y-flow can be rerouted"() {
        given: "A qinq y-flow"
        def swT = switchTriplets.all().withAllDifferentEndpoints().withoutWBSwitch().getSwitchTriplets().find {
            def yPoints = topologyHelper.findPotentialYPoints(it)
             yPoints.size() == 1 && yPoints[0] != it.shared
        }
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")

        def yFlow = yFlowFactory.getBuilder(swT).withEp1QnQ().withEp2QnQ().withSharedEpQnQ()
                .build().create()

        def paths = yFlow.retrieveAllEntityPaths()
        def islToFail = paths.subFlowPaths.first().getInvolvedIsls().first()

        when: "Fail a flow ISL (bring switch port down)"
        islHelper.breakIsl(islToFail)

        then: "The flow was rerouted after reroute delay"
        yFlow.waitForBeingInState(FlowState.IN_PROGRESS)

        and: "History has relevant entries about y-flow reroute"
        wait(FLOW_CRUD_TIMEOUT) {
            assert yFlow.retrieveFlowHistory().getEntriesByType(YFlowActionType.REROUTE).last()
                    .payload.find { it.action == YFlowActionType.REROUTE.payloadLastAction }
        }

        yFlow.subFlows.each { sf ->
            assert yFlow.retrieveSubFlowHistory(sf.flowId).getEntriesByType(FlowActionType.REROUTE).last()
                    .payload.find { it.action == FlowActionType.REROUTE.payloadLastAction }
        }
        def newPath = null
        wait(rerouteDelay + WAIT_OFFSET) {
            assert yFlow.retrieveDetails().status == FlowState.UP
            newPath = yFlow.retrieveAllEntityPaths()
            assert newPath != paths
        }

        and: "Y-flow passes flow validation"
        yFlow.validate().asExpected

        and: "Both sub-flows pass flow validation"
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).every { it.asExpected }
        }

        and: "All involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(newPath.getInvolvedSwitches()).isEmpty()

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def traffExam = traffExamProvider.get()
        def exam = yFlow.traffExam(traffExam, yFlow.maximumBandwidth, 10)
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
    }

    @Tags([LOW_PRIORITY])
    def "Y-Flow reroute has not been executed when both sub-flows are on the best path"() {
        given: "Y-Flow has been created successfully"
        def swT = switchTriplets.all().withAllDifferentEndpoints().first()
        def yFlow = yFlowFactory.getRandom(swT, false)
        def yFlowPathBeforeReroute = yFlow.retrieveAllEntityPaths()

        when: "Y-Flow reroute has been called"
        yFlow.reroute()

        then: "The appropriate error has been returned"
        def actualException = thrown(HttpClientErrorException)
        new YFlowRerouteExpectedError(~/Reroute is unsuccessful. Couldn't find new path\(s\)/).matches(actualException)

        and: "Y-Flow path has not been changed"
        def yFlowPathAfterReroute = yFlow.retrieveAllEntityPaths()
        verifyAll {
            yFlow.retrieveDetails().status == FlowState.UP
            yFlowPathAfterReroute == yFlowPathBeforeReroute
        }
    }

    @Tags([LOW_PRIORITY])
    def "Y-Flow reroute has been executed when more preferable path is available for both sub-flows (shared path cost was changed)" () {
        given: "The appropriate switches have been collected"
        //y-flow with shared path is created when shared_ep+ep1->neighbour && ep1+ep2->neighbour && shared_ep+ep2->not neighbour
        def swT = switchTriplets.all().withAllDifferentEndpoints().withSharedEpEp1Ep2InChain().random()

        and: "The ISLs cost between switches has been changed to make preferable path"
        List<Isl> directSwTripletIsls = (swT.pathsEp1[0].size() == 2 ?
                swT.pathsEp2.findAll { it.size() == 4 && it.containsAll(swT.pathsEp1[0]) } :
                swT.pathsEp1.findAll { it.size() == 4 && it.containsAll(swT.pathsEp2[0]) })
                .collectMany { pathHelper.getInvolvedIsls(it) }.collectMany { [it, it.reversed] }.unique()
        islHelper.updateIslsCost(directSwTripletIsls, 1)

        and: "Y-Flow with shared path has been created successfully"
        def yFlow = yFlowFactory.getRandom(swT, false)
        def yFlowPathBeforeReroute = yFlow.retrieveAllEntityPaths()

        and: "Shared ISLs cost has been changed to provide on-demand Y-Flow reroute"
        def sharedPathIslBeforeReroute = yFlowPathBeforeReroute.sharedPath.getInvolvedIsls()
        islHelper.updateIslsCost(sharedPathIslBeforeReroute, 80000)

        when: "Y-Flow reroute has been called"
        YFlowRerouteResult rerouteDetails = yFlow.reroute()

        then: "Y-Flow reroute has been executed successfully"
        verifyAll {
            rerouteDetails.rerouted
            rerouteDetails.subFlowPaths.size() == 2
        }

        and: "Both sub-flows paths have been changed"
        yFlow.waitForBeingInState(FlowState.UP, FLOW_CRUD_TIMEOUT)

        def yFlowPathAfterReroute = yFlow.retrieveAllEntityPaths()
        def sharedPathIslAfterReroute = yFlowPathAfterReroute.sharedPath.getInvolvedIsls()
        verifyAll {
            sharedPathIslAfterReroute.sort() != sharedPathIslBeforeReroute.sort()
            yFlowPathAfterReroute.subFlowPaths.first().path.forward != yFlowPathBeforeReroute.subFlowPaths.first().path.forward
            yFlowPathAfterReroute.subFlowPaths.first().path.reverse != yFlowPathBeforeReroute.subFlowPaths.first().path.reverse
            yFlowPathAfterReroute.subFlowPaths.last().path.forward != yFlowPathBeforeReroute.subFlowPaths.last().path.forward
            yFlowPathAfterReroute.subFlowPaths.last().path.reverse != yFlowPathBeforeReroute.subFlowPaths.last().path.reverse
        }
    }

    @Tags([LOW_PRIORITY])
    def "Y-Flow reroute has been executed when more preferable path is available for one of the sub-flows" () {
        given: "The appropriate switches have been collected"
        //y-flow with shared path is created when shared_ep+ep1->neighbour && ep1+ep2->neighbour && shared_ep+ep2->not neighbour
        def swT = switchTriplets.all().withAllDifferentEndpoints().withSharedEpEp1Ep2InChain().random()

        and: "The ISLs cost between switches has been changed to make preferable path"
        List<Isl> directSwTripletIsls = (swT.pathsEp1[0].size() == 2 ?
                swT.pathsEp2.findAll { it.size() == 4  && it.containsAll(swT.pathsEp1[0])} :
                swT.pathsEp1.findAll { it.size() == 4 && it.containsAll(swT.pathsEp2[0])})
                .collectMany { pathHelper.getInvolvedIsls(it) }.collectMany{[it, it.reversed]}.unique()
        islHelper.updateIslsCost(directSwTripletIsls, 1)

        and: "Y-Flow with shared path has been created successfully"
        def yFlow = yFlowFactory.getRandom(swT, false)
        def yFlowPathBeforeReroute = yFlow.retrieveAllEntityPaths()
        assert !yFlowPathBeforeReroute.sharedPath.path.isPathAbsent()

        and: "The required ISLs cost has been updated to make manual reroute available"
        def islsSubFlow1 = (yFlowPathBeforeReroute.subFlowPaths.first().getInvolvedIsls(Direction.FORWARD)
                + yFlowPathBeforeReroute.subFlowPaths.first().getInvolvedIsls(Direction.REVERSE)).unique()

        def islsSubFlow2 = (yFlowPathBeforeReroute.subFlowPaths.last().getInvolvedIsls(Direction.FORWARD)
                + yFlowPathBeforeReroute.subFlowPaths.last().getInvolvedIsls(Direction.REVERSE)).unique()

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
        islHelper.updateIslsCost(islsToModify, 80000)

        when: "Y-Flow reroute has been called"
        YFlowRerouteResult rerouteDetails = yFlow.reroute()

        then: "Y-Flow has been rerouted successfully"
        verifyAll {
            rerouteDetails.rerouted
            rerouteDetails.subFlowPaths.size() == 2
        }

        and: "The appropriate flow has been rerouted"
        yFlow.waitForBeingInState(FlowState.UP, FLOW_CRUD_TIMEOUT)

        def yFlowPathAfterReroute = yFlow.retrieveAllEntityPaths()
        verifyAll {
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId == subFlowId }.path.forward != yFlowPathBeforeReroute.subFlowPaths.find { it.flowId == subFlowId }.path.forward
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId == subFlowId }.path.reverse != yFlowPathBeforeReroute.subFlowPaths.find { it.flowId == subFlowId }.path.reverse
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId != subFlowId }.path.forward == yFlowPathBeforeReroute.subFlowPaths.find { it.flowId != subFlowId }.path.forward
            yFlowPathAfterReroute.subFlowPaths.find { it.flowId != subFlowId }.path.reverse == yFlowPathBeforeReroute.subFlowPaths.find { it.flowId != subFlowId }.path.reverse
        }
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "Y-Flow reroute has not been executed when one sub-flow is on the best path and there is no alternative path for another sub-flow due to the down ISLs" () {
        given: "Y-Flow has been created successfully"
        def swT = switchTriplets.all().withAllDifferentEndpoints().first()
        def yFlow = yFlowFactory.getRandom(swT, false)
        def yFlowPathBeforeReroute = yFlow.retrieveAllEntityPaths()

        and: "Sub-flows not intersected ISLs have been collected"
        def islsSubFlow1 = yFlowPathBeforeReroute.subFlowPaths.first().getInvolvedIsls()
        def islsSubFlow2 = yFlowPathBeforeReroute.subFlowPaths.last().getInvolvedIsls()

        def notIntersectedIsls = islsSubFlow1.size() > islsSubFlow2.size() ?
                islsSubFlow1.findAll { !(it in islsSubFlow2) } : islsSubFlow2.findAll { !(it in islsSubFlow1) }

        and: "Switch off all ISLs on the terminal switch"
        Switch terminalSwitch = notIntersectedIsls.last().dstSwitch
        def broughtDownIsls = topology.getRelatedIsls(terminalSwitch)
        islHelper.breakIsls(broughtDownIsls)
        yFlow.waitForBeingInState(FlowState.DEGRADED, FLOW_CRUD_TIMEOUT)

        when: "Y-Flow reroute has been called"
        yFlow.reroute()

        then: "The appropriate error has been returned"
        def actualException = thrown(HttpClientErrorException)
        new YFlowRerouteExpectedError(~/Not enough bandwidth or no path found. Switch ${terminalSwitch.dpId} doesn't have links with enough bandwidth/).matches(actualException)

        and: "Y-Flow path has not been changed"
        def yFlowPathAfterReroute = yFlow.retrieveAllEntityPaths()
        verifyAll {
            yFlow.retrieveDetails().status == FlowState.DEGRADED
            yFlowPathAfterReroute == yFlowPathBeforeReroute
        }
    }
}
