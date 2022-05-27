package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_SUCCESS_Y
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
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

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid y-flow can be rerouted"() {
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        given: "A qinq y-flow"
        def swT = topologyHelper.switchTriplets.find { it ->
            def yPoints = yFlowHelper.findPotentialYPoints(it)
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
        antiflap.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)
        wait(WAIT_OFFSET) { northbound.getLink(islToFail).state == FAILED }

        then: "The flow was rerouted after reroute delay"
        and: "History has relevant entries about y-flow reroute"
        wait(FLOW_CRUD_TIMEOUT) {
            assert northbound.getFlowHistory(yFlow.YFlowId).last().payload.last().action == REROUTE_SUCCESS_Y
        }
        yFlow.subFlows.each { sf ->
            assert northbound.getFlowHistory(sf.flowId).last().payload.last().action == REROUTE_SUCCESS
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
        def involvedSwitches = pathHelper.getInvolvedYSwitches(paths)
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
        statsHelper.verifyYFlowWritesMeterStats(yFlow, beforeTraffic, true)
        yFlow.subFlows.each {
            statsHelper.verifyFlowWritesStats(it.flowId, beforeTraffic, true)
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        islToFail && antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        wait(WAIT_OFFSET) { northbound.getLink(islToFail).state == DISCOVERED }
        database.resetCosts(topology.isls)
    }
}
