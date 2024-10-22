package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.yflow.YFlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.yflow.YFlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.YFlowBuilder
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowActionType
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.model.SwitchFeature
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Slf4j
@Narrative("Verify create operations on y-flows.")
class YFlowCreateSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid Y-Flow can be created#trafficDisclaimer, covered cases: #coveredCases"() {
        assumeTrue(swT != null, "These cases cannot be covered on given topology: $coveredCases")

        when: "Create a Y-Flow of certain configuration"
        def allLinksBefore = northbound.getAllLinks()
        def yFlow = yFlowBuilder.build().create()

        then: "Y-Flow has been created successfully"
        yFlow.yPoint

        and: "2 sub-flows are created, visible via regular 'dump flows' API"
        def regularFlowIds = northboundV2.getAllFlows()*.flowId
        yFlow.subFlows.first().flowId in regularFlowIds
        yFlow.subFlows.last().flowId in regularFlowIds

        and: "History has relevant entries about y-Flow creation"
        yFlow.waitForHistoryEvent(YFlowActionType.CREATE, FLOW_CRUD_TIMEOUT)
        [yFlow.subFlows.first().flowId, yFlow.subFlows.last().flowId].each { flowId ->
            Wrappers.wait(FLOW_CRUD_TIMEOUT) {
                assert yFlow.retrieveSubFlowHistory(flowId).getEntriesByType(FlowActionType.CREATE).last()
                        .payload.find { it.action == FlowActionType.CREATE.payloadLastAction }
            }
        }

        and: "User is able to view Y-Flow paths"
        def paths = yFlow.retrieveAllEntityPaths()

        and: "Y-Flow passes flow validation"
        with(yFlow.validateAndCollectDiscrepancy()) {
            it.asExpected
            it.subFlowsDiscrepancies.isEmpty()
        }

        and: "Both sub-flows pass flow validation"
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).every { it.asExpected }
        }

        and: "YFlow is pingable"
        if (swT.shared != swT.ep1 || swT.shared != swT.ep2) {
            def response = yFlow.pingAndCollectDiscrepancies()
            !response.error
            response.subFlowsDiscrepancies.isEmpty()
        }

        and: "All involved switches pass switch validation"
        def involvedSwitches = paths.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        and: "Bandwidth is properly consumed on shared and non-shared ISLs(not applicable for single switch Y-Flow)"
        def allLinksAfter = northbound.getAllLinks()
        def involvedIslsSFlow_1 = paths.subFlowPaths.first().getInvolvedIsls()
        def involvedIslsSFlow_2 = paths.subFlowPaths.last().getInvolvedIsls()

        if(!swT.singleSwitch) {
            (involvedIslsSFlow_1 + involvedIslsSFlow_2).unique().each { link ->
                [link, link.reversed].each {
                    islUtils.getIslInfo(allLinksBefore, it).ifPresent(islBefore -> {
                        def bwBefore = islBefore.availableBandwidth
                        def bwAfter = islUtils.getIslInfo(allLinksAfter, it).get().availableBandwidth
                        assert bwBefore == bwAfter + yFlow.maximumBandwidth
                    })
                }
            }
        }

        and: "YFlow is pingable #2"
        if (swT.shared != swT.ep1 || swT.shared != swT.ep2) {
            //TODO: remove this quickfix for failing traffexam
            !yFlow.ping().error
        }

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        if (trafficApplicable) {
            def exam = yFlow.traffExam(traffExam, yFlow.maximumBandwidth, 10)
            examReports = withPool {
                [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    traffExam.waitExam(direction)
                }
            }
        }

        then: "Traffic flows on both sub-flows, but does not exceed the Y-Flow bandwidth restriction (~halves for each sub-flow)"
        if (trafficApplicable) {
            def assertions = new SoftAssertions()
            examReports.each { report ->
                assertions.checkSucceeds {
//                    def flowBwBits = yFlow.maximumBandwidth * 1000
                    //allow 10% deviation
//                    assert Math.abs(report.producerReport.bitsPerSecond - flowBwBits / 2) < flowBwBits * 0.1, report.exam
                    assert report.hasTraffic(), report.exam
                }
            }
            //https://github.com/telstra/open-kilda/issues/5292
            try {
                assertions.verify()
            } catch (Exception exception) {
                if (specificationContext.getCurrentIteration().getDataVariables().get("coveredCases")[0]
                        != "se qinq, ep1 default, ep2 qinq") {
                    throw exception
                } else {
                    assumeTrue(false, "https://github.com/telstra/open-kilda/issues/5292" +
                            specificationContext.getCurrentIteration().getDataVariables())
                }
            }
        }

        when: "Delete the y-flow"
        yFlow.delete()

        then: "Y-Flow and related sub-flows are removed"
        verifyAll(northboundV2.getAllFlows()) { allRegularFlows ->
            assert !(yFlow.subFlows.first().flowId in allRegularFlows.flowId)
            assert !(yFlow.subFlows.last().flowId in allRegularFlows.flowId)
            assert !(yFlow.yFlowId in allRegularFlows.YFlowId)
        }

        and: "History of each subFlows has relevant entries about flow deletion"
        [yFlow.subFlows.first().flowId, yFlow.subFlows.last().flowId].each { flowId ->
            Wrappers.wait(FLOW_CRUD_TIMEOUT) {
                assert yFlow.retrieveSubFlowHistory(flowId).getEntriesByType(FlowActionType.DELETE).last()
                        .payload.find { it.action == FlowActionType.DELETE.payloadLastAction }
            }
        }

        and: "All involved switches pass switch validation"
        Wrappers.wait(WAIT_OFFSET) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }

        where:
        //Not all cases may be covered. Uncovered cases will be shown as a 'skipped' test
        data << getFLowsTestData()
        swT = data.swT as SwitchTriplet
        yFlowBuilder = data.yFlowBuilder as YFlowBuilder
        coveredCases = data.coveredCases as List<String>
        trafficApplicable = isTrafficApplicable(swT)
        trafficDisclaimer = trafficApplicable ? " and pass traffic" : " [!NO TRAFFIC CHECK!]"
    }

    def "System forbids to create a Y-Flow with conflict: #data.descr"(YFlowBuilder yFlowBuilder) {
        assumeTrue(data.yFlowBuilder != null, "This case cannot be covered on given topology: $data.descr")

        when: "Try creating a Y-Flow with one endpoint being in conflict with the other one"
        yFlowBuilder.build().create()

        then: "Error is received, describing the problem"
        def exc = thrown(HttpClientErrorException)
        new YFlowNotCreatedExpectedError(data.errorPattern(yFlowBuilder)).matches(exc)

        and: "'Get' Y-Flows doesn't return the flow"
        assert !northboundV2.getYFlow(yFlowBuilder.yFlow.yFlowId)

        and: "'Get' flows doesn't return the sub-flows"
        !(yFlowBuilder.yFlow.yFlowId in northboundV2.getAllFlows().YFlowId)

        where: "Use different types of conflicts"
        data << [
                [
                        descr       : "subflow1 and subflow2 same vlan on shared endpoint",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withSameSharedEndpointsVlan(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The sub-flows .* and .* have shared endpoint conflict: \
SubFlowSharedEndpointEncapsulation\(vlanId=${flow.yFlow.subFlows.first().sharedEndpoint.vlanId}, innerVlanId=0\) \/ \
SubFlowSharedEndpointEncapsulation\(vlanId=${flow.yFlow.subFlows.last().sharedEndpoint.vlanId}, innerVlanId=0\)/
                        }
                ],
                [
                        descr       : "subflow1 and subflow2 no vlan on shared endpoint",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withSharedEndpointsVlan(0, 0),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The sub-flows .*? and .*? have shared endpoint conflict: \
SubFlowSharedEndpointEncapsulation\(vlanId=0, innerVlanId=0\) \/ \
SubFlowSharedEndpointEncapsulation\(vlanId=0, innerVlanId=0\)/
                        }
                ],
                [
                        descr       : "ep1 = ep2, same vlan",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withEp1AndEp2SameSwitchAndPort()
                                .withEp1VlanSameAsEp2Vlan(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The sub-flows .*? and .*? have endpoint conflict: \
switchId="${flow.yFlow.subFlows.first().endpoint.switchId}" port=${flow.yFlow.subFlows.first().endpoint.portNumber} vlanId=${flow.yFlow.subFlows.first().endpoint.vlanId} \/ \
switchId="${flow.yFlow.subFlows.last().endpoint.switchId}" port=${flow.yFlow.subFlows.last().endpoint.portNumber} vlanId=${flow.yFlow.subFlows.last().endpoint.vlanId}/
                        }
                ],
                [
                        descr       : "ep1 = ep2, both no vlan",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withEp1AndEp2SameSwitchAndPort()
                                .withEp1AndEp2Vlan(0, 0),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The sub-flows .*? and .*? have endpoint conflict: \
switchId="${flow.yFlow.subFlows.first().endpoint.switchId}" port=${flow.yFlow.subFlows.first().endpoint.portNumber} \/ \
switchId="${flow.yFlow.subFlows.first().endpoint.switchId}" port=${flow.yFlow.subFlows.last().endpoint.portNumber}/
                        }
                ],
                [
                        descr       : "ep1 = ep2, vlans [0,X] and [X,0]",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withEp1AndEp2SameSwitchAndPort()
                                .withEp2QnqAsEp1Vlan(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The sub-flows .*? and .*? have endpoint conflict: \
switchId="${flow.yFlow.subFlows.first().endpoint.switchId}" port=${flow.yFlow.subFlows.first().endpoint.portNumber} vlanId=${flow.yFlow.subFlows.first().endpoint.vlanId} \/ \
switchId="${flow.yFlow.subFlows.last().endpoint.switchId}" port=${flow.yFlow.subFlows.last().endpoint.portNumber} vlanId=${flow.yFlow.subFlows.first().endpoint.vlanId}/
                        }
                ],
                [
                        descr       : "ep1 on ISL port",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withEp1OnISLPort(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The port ${flow.yFlow.subFlows.first().endpoint.portNumber} on the \
switch '${flow.yFlow.subFlows.first().endpoint.switchId}' is occupied by an ISL \(destination endpoint collision\)./
                        }
                ],
                [
                        descr       : "shared endpoint on ISL port",
                        yFlowBuilder: yFlowFactory.getBuilder(switchTriplets.all().first()).withSharedEpOnISLPort(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/The port ${flow.yFlow.sharedEndpoint.portNumber} on the \
switch '${flow.yFlow.sharedEndpoint.switchId}' is occupied by an ISL \(source endpoint collision\)./
                        }
                ],
                [
                        descr       : "ep2 on s42 port",
                        yFlowBuilder: {
                            def swTriplet = switchTriplets.all(true).getSwitchTriplets().find { it.ep2.prop?.server42Port }
                            if (swTriplet) {
                                return yFlowFactory.getBuilder(swTriplet).withEp2Port(swTriplet.ep2.prop.server42Port)
                            }
                            return null
                        }(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/Server 42 port in the switch properties for switch '${flow.yFlow.subFlows.last().endpoint.switchId}'\
 is set to '${flow.yFlow.subFlows.last().endpoint.portNumber}'. It is not possible to create or update an endpoint with these parameters./
                        }
                ],
                [
                        descr       : "shared endpoint on s42 port",
                        yFlowBuilder: {
                            def swTriplet = switchTriplets.all().getSwitchTriplets().find { it.shared.prop?.server42Port }
                            if (swTriplet) {
                                return yFlowFactory.getBuilder(swTriplet).withSharedEpPort(swTriplet.shared.prop.server42Port)
                            }
                            return null
                        }(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/Server 42 port in the switch properties for switch '${flow.yFlow.sharedEndpoint.switchId}'\
 is set to '${flow.yFlow.sharedEndpoint.portNumber}'. It is not possible to create or update an endpoint with these parameters./
                        }
                ],
                [
                        descr       : "negative shared endpoint port number",
                        yFlowBuilder: {
                            def swTriplet = switchTriplets.all().random()
                            if (swTriplet) {
                                return yFlowFactory.getBuilder(swTriplet).withSharedEpPort(-1)
                            }
                            return null
                        }(),
                        errorPattern: { YFlowBuilder flow ->
                            ~/Errors: PortNumber must be non-negative/
                        }
                ]
        ]
        yFlowBuilder = data.yFlowBuilder as YFlowBuilder
    }

    def "System forbids to create a Y-Flow with conflict: subflow1 vlans are [0,X] and subflow2 vlans are [X,0] on shared endpoint"() {
        when: "Try creating a Y-Flow with one endpoint being in conflict with the other one"
        def flowParams = yFlowFactory.getBuilder(switchTriplets.all().first()).withSubFlow1SharedEpQnqAsSubFlow2SharedEpVlan()
        def yFlow = flowParams.build().create()

        then: "Error is received, describing the problem"
        def exc = thrown(HttpClientErrorException)
        new YFlowNotCreatedWithConflictExpectedError(~/FlowValidateAction failed: \
Requested flow '.*?' conflicts with existing flow '.*?'. Details: requested flow '.*?' \
source: switchId="${flowParams.yFlow.sharedEndpoint.switchId}" port=${flowParams.yFlow.sharedEndpoint.portNumber} vlanId=${flowParams.yFlow.subFlows.last().sharedEndpoint.innerVlanId}, \
existing flow '.*?' \
source: switchId="${flowParams.yFlow.sharedEndpoint.switchId}" port=${flowParams.yFlow.sharedEndpoint.portNumber} vlanId=${flowParams.yFlow.subFlows.first().sharedEndpoint.vlanId}/)
                .matches(exc)
        and: "'Get' y-flows doesn't return the flow"
        Wrappers.wait(WAIT_OFFSET) { //even on error system briefly creates an 'in progress' flow
            assert !yFlow || !northboundV2.getYFlow(yFlow.yFlowId)
        }

        and: "'Get' flows doesn't return the sub-flows"
        if (yFlow) {
            northboundV2.getAllFlows().forEach {
                assert it.YFlowId != yFlow.yFlowId
            }
        }
    }

    @Tags([HARDWARE])
    def "System forbids to create a Y-Flow with conflict: shared endpoint port is inside a LAG group"() {
        given: "A LAG port"
        def swT = switchTriplets.all().getSwitchTriplets().find { it.shared.features.contains(SwitchFeature.LAG) }
        assumeTrue(swT != null, "Unable to find a switch that supports LAG")
        def portsArray = topology.getAllowedPortsForSwitch(swT.shared)[-2, -1] as Set
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = switchHelper.createLagLogicalPort(swT.shared.dpId, portsArray).logicalPortNumber

        when: "Try creating a Y-Flow with shared endpoint port being inside LAG"
        def yFlow = yFlowFactory.getBuilder(swT).withSharedEpPort(portsArray[0]).build().create()

        then: "Error is received, describing the problem"
        def exc = thrown(HttpClientErrorException)
        new YFlowNotCreatedExpectedError(
                ~/Port ${portsArray[0]} on switch $swT.shared.dpId is used as part of LAG port $lagPort/).matches(exc)
        and: "'Get' y-flows doesn't return the flow"
        Wrappers.wait(WAIT_OFFSET) { //even on error system briefly creates an 'in progress' flow
            assert !yFlow || !northboundV2.getYFlow(yFlow.yFlowId)
        }

        and: "'Get' flows doesn't return the sub-flows"
        if (yFlow) {
            northboundV2.getAllFlows().forEach {
                assert it.YFlowId != yFlow.yFlowId
            }
        }
    }

    @Tags([LOW_PRIORITY])
    def "System allows to create Y-Flow with bandwidth equal to link bandwidth between shared endpoint and y-point (#4965)"() {
        /* Shared <----------------> Y-Point ----------- Ep1
                         ⬆              \ ______________ Ep2
          flow max_bandwidth == bw of this link         ↖
                                                        flow max_bandwidth <= bw on these two links
        */

        given: "three switches and potential Y-Flow point"
        def slowestLinkOnTheWest = database.getIsls(topology.getIsls()).sort { it.getMaxBandwidth() }.first()
        def slowestLinkSwitchIds = [slowestLinkOnTheWest.getSrcSwitchId(), slowestLinkOnTheWest.getDestSwitchId()]
        def switchTriplet = switchTriplets.all(true, false).getSwitchTriplets()
                .find {
                    def yPoints = topologyHelper.findPotentialYPoints(it)
                    slowestLinkSwitchIds.contains(it.shared.getDpId()) &&
                            !slowestLinkSwitchIds.intersect(yPoints).isEmpty()
                }
        assumeTrue(switchTriplet != null, "No suiting switches found.")

        when: "Y-Flow plan for them with bandwidth equal to ISL bandwidth"
        def yFlow = yFlowFactory.getBuilder(switchTriplet, false)
                .withBandwidth(slowestLinkOnTheWest.getMaxBandwidth()).build()

        then: "Y-Flow is created and UP"
        yFlow.create()
    }

    /**
     * First N iterations are covering all unique se-yp-ep combinations from 'getSwTripletsTestData' with random vlans.
     * Then add required iterations of unusual vlan combinations (default port, qinq, etc.)
     */
    def getFLowsTestData() {
        List<Map> testData = getSwTripletsTestData()
        //random vlans on getSwTripletsTestData
        testData.findAll { it.swT != null }.each {
            it.yFlowBuilder = yFlowFactory.getBuilder(it.swT)
            it.coveredCases << "random vlans"
        }
        //se noVlan+vlan, ep1-ep2 same sw-port, vlan+noVlan
        testData.with {
            List<SwitchTriplet> suitingTriplets = owner.switchTriplets.all().getSwitchTriplets().findAll { it.ep1 == it.ep2 }
            def swT = suitingTriplets.find { isTrafficApplicable(it) } ?: suitingTriplets[0]
            def yFlowBuilder = owner.yFlowFactory.getBuilder(swT).withSubFlow2SharedEp(0)
                    .withEp1AndEp2SameSwitchAndPort().withEp1Vlan(0)
            add([swT: swT, yFlowBuilder: yFlowBuilder, coveredCases: ["se noVlan+vlan, ep1-ep2 same sw-port, vlan+noVlan"]])
        }
        //se same vlan+qinq, ep1 default, ep2 qinq
        testData.with {
            def suitingTriplets = owner.switchTriplets.all().getSwitchTriplets().findAll { it.ep1 != it.ep2 }
            def swT = suitingTriplets.find { isTrafficApplicable(it) } ?: suitingTriplets[0]
            def yFlowBuilder = owner.yFlowFactory.getBuilder(swT)
                    .withSameSharedEndpointsVlan().withSharedEpQnQ().withEp1Vlan(0).withEp2QnQ()
            add([swT: swT, yFlowBuilder: yFlowBuilder, coveredCases: ["se same vlan+qinq, ep1 default, ep2 qinq"]])
        }
        //se qinq, ep1-ep2 same sw-port, qinq
        testData.with {
            def suitingTriplets = owner.switchTriplets.all().getSwitchTriplets().findAll { it.ep1 == it.ep2 }
            def swT = suitingTriplets.find { isTrafficApplicable(it) } ?: suitingTriplets[0]
            def yFlowBuilder = owner.yFlowFactory.getBuilder(swT)
                    .withSharedEpQnQ().withEp1AndEp2SameSwitchAndPort().withEp1QnQ().withEp2QnQ()
            add([swT: swT, yFlowBuilder: yFlowBuilder, coveredCases: ["se qinq, ep1-ep2 same sw-port, qinq"]])
        }

        //se-ep1-ep2 same sw (one-switch Y-Flow)
        testData.with {
            def topo = owner.topology
            def sw = topo.getActiveSwitches()
                    .sort { swIt -> topo.getActiveTraffGens().findAll { it.switchConnected.dpId == swIt.dpId }.size() }
                    .reverse()
                    .first()
            def swT = owner.switchTriplets.all(false, true).getSwitchTriplets().find() {
                it.shared == sw && it.ep1 == sw && it.ep2 == sw
            }
            def yFlowBuilder = owner.yFlowFactory.getBuilder(swT)
            add([swT: swT, yFlowBuilder: yFlowBuilder, coveredCases: ["se-ep1-ep2 same sw (one-switch Y-Flow)"]])
        }

        return testData
    }

    def getSwTripletsTestData() {
        def requiredCases = [
                //se = shared endpoint, ep = subflow endpoint, yp = y-point
                [name     : "se is wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared.dpId
                 }],
                [name     : "se is non-wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared.dpId
                 }],
                [name     : "ep on wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "ep on non-wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> !swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "se+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared.dpId
                 }],
                [name     : "se+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared.dpId
                 }],
                [name     : "yp on wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared.dpId && yPoints[0] != swT.ep1.dpId && yPoints[0] != swT.ep2.dpId
                 }],
                [name     : "yp on non-wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared.dpId && yPoints[0] != swT.ep1.dpId && yPoints[0] != swT.ep2.dpId
                 }],
                [name     : "ep+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1.dpId || yPoints[0] == swT.ep2.dpId)
                 }],
                [name     : "ep+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1.dpId || yPoints[0] == swT.ep2.dpId)
                 }],
                [name     : "yp==se",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     yPoints.size() == 1 && yPoints[0] == swT.shared.dpId && swT.shared != swT.ep1 && swT.shared != swT.ep2
                 }]
        ]
        requiredCases.each { it.picked = false }
        //match all triplets to the list of requirements that it satisfies
        Map<SwitchTriplet, List<String>> weightedTriplets = switchTriplets.all(false, true).getSwitchTriplets()
                .collectEntries { triplet ->
                    [(triplet): requiredCases.findAll { it.condition(triplet) }*.name]
                }
        //sort, so that most valuable triplet is first
        weightedTriplets = weightedTriplets.sort { -it.value.size() }
        def result = []
        //greedy alg. Pick most valuable triplet. Re-weigh remaining triplets considering what is no longer required and repeat
        while (requiredCases.find { !it.picked } && weightedTriplets.entrySet()[0].value.size() > 0) {
            def pick = weightedTriplets.entrySet()[0]
            weightedTriplets.remove(pick.key)
            pick.value.each { satisfiedCase ->
                requiredCases.find { it.name == satisfiedCase }.picked = true
            }
            weightedTriplets.entrySet().each { it.value.removeAll(pick.value) }
            weightedTriplets = weightedTriplets.sort { -it.value.size() }
            result << [swT: pick.key, coveredCases: pick.value]
        }
        def notPicked = requiredCases.findAll { !it.picked }
        if (notPicked) {
            //special entry, passing cases that are not covered for later processing
            result << [swT: null, coveredCases: notPicked*.name]
        }
        return result
    }

    static boolean isTrafficApplicable(SwitchTriplet swT) {
        def isOneSw = swT ? (swT.shared == swT.ep1 && swT.shared == swT.ep2) : false
        def amountTraffgens = isOneSw ? 1 : 0
        swT ? [swT.shared, swT.ep1, swT.ep2].every { it.traffGens.size() > amountTraffgens } : false
    }
}
