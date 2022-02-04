package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS_Y
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS_Y
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchFeature
import org.openkilda.northbound.dto.v2.switches.CreateLagPortDto
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.openkilda.testing.tools.SoftAssertions

import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Slf4j
@Narrative("Verify create operations on y-flows.")
class YFlowCreateSpec extends HealthCheckSpecification {
    @Autowired @Shared
    YFlowHelper yFlowHelper
    @Autowired @Shared
    Provider<TraffExamService> traffExamProvider

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid y-flow can be created#trafficDisclaimer, covered cases: #coveredCases"() {
        assumeTrue(swT != null, "These cases cannot be covered on given topology: $coveredCases")
        if (coveredCases.toString().contains("qinq")) {
            assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        }

        when: "Create a y-flow of certain configuration"
        def allLinksBefore = northbound.getAllLinks()
        def yFlow = northboundV2.addYFlow(yFlowRequest)

        then: "Y-flow is created and has UP status"
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { assert northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString() }
        northboundV2.getYFlow(yFlow.YFlowId).YPoint

        and: "2 sub-flows are created, visible via regular 'dump flows' API"
        northboundV2.getAllFlows()*.flowId.sort() == yFlow.subFlows*.flowId.sort()

        and: "History has relevant entries about y-flow creation"
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { northbound.getFlowHistory(yFlow.YFlowId).last().payload.last().action == CREATE_SUCCESS_Y }
        yFlow.subFlows.each { sf ->
            Wrappers.wait(FLOW_CRUD_TIMEOUT) { assert northbound.getFlowHistory(sf.flowId).last().payload.last().action == CREATE_SUCCESS }
        }

        and: "User is able to view y-flow paths"
        def paths = northboundV2.getYFlowPaths(yFlow.YFlowId)

        and: "Y-flow passes flow validation"
        with(northboundV2.validateYFlow(yFlow.YFlowId)) {
            it.asExpected
            it.subFlowValidationResults.each { assert it.asExpected }
        }

        and: "Both sub-flows pass flow validation"
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).every { it.asExpected }
        }

        and: "YFlow is pingable"
        def response = northboundV2.pingYFlow(yFlow.YFlowId, new YFlowPingPayload(2000))
        !response.error
        response.subFlows.each {
            assert it.forward.pingSuccess
            assert it.reverse.pingSuccess
        }

        and: "All involved switches pass switch validation"
        def involvedSwitches = pathHelper.getInvolvedYSwitches(paths)
//        involvedSwitches.each { sw ->
//            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
//            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
//        }

        and: "Bandwidth is properly consumed on shared and non-shared ISLs"
        def allLinksAfter = northbound.getAllLinks()
        def involvedIslsSFlow_1 = pathHelper.getInvolvedIsls(yFlow.subFlows[0].flowId)
        def involvedIslsSFlow_2 = pathHelper.getInvolvedIsls(yFlow.subFlows[1].flowId)

        (involvedIslsSFlow_1 + involvedIslsSFlow_2).unique().each { link ->
            [link, link.reversed].each {
                def bwBefore = islUtils.getIslInfo(allLinksBefore, it).get().availableBandwidth
                def bwAfter = islUtils.getIslInfo(allLinksAfter, it).get().availableBandwidth
                assert bwBefore == bwAfter + yFlow.maximumBandwidth
            }
        }

        and: "YFlow is pingable #2"
        //TODO: remove this quickfix for failing traffexam
        !northboundV2.pingYFlow(yFlow.YFlowId, new YFlowPingPayload(2000)).error

        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def beforeTraffic = new Date()
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        if (trafficApplicable) {
            def exam = new FlowTrafficExamBuilder(topology, traffExam).buildYFlowExam(yFlow, yFlow.maximumBandwidth, 5)
            examReports = withPool {
                [exam.forward1, exam.reverse1, exam.forward2, exam.reverse2].collectParallel { Exam direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    traffExam.waitExam(direction)
                }
            }
        }

        then: "Traffic flows on both sub-flows, but does not exceed the y-flow bandwidth restriction (~halves for each sub-flow)"
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
            assertions.verify()
        }

        and: "Y-flow and subflows stats are available (flow.raw.bytes)"
//        statsHelper.verifyFlowWritesStats(yFlow.YFlowId, beforeTraffic, trafficApplicable)
        yFlow.subFlows.each {
            statsHelper.verifyFlowWritesStats(it.flowId, beforeTraffic, trafficApplicable)
        }

        when: "Delete the y-flow"
        northboundV2.deleteYFlow(yFlow.YFlowId)

        then: "The y-flow is no longer visible via 'get' API"
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getAllYFlows().empty }
        def flowRemoved = true

        and: "Related sub-flows are removed"
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getAllFlows().empty }

        and: "History has relevant entries about y-flow deletion"
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { northbound.getFlowHistory(yFlow.YFlowId).last().payload.last().action == DELETE_SUCCESS_Y }
        yFlow.subFlows.each { sf ->
            Wrappers.wait(FLOW_CRUD_TIMEOUT) {
                assert northbound.getFlowHistory(sf.flowId).last().payload.last().action == DELETE_SUCCESS
            }
        }

        and: "All involved switches pass switch validation"
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(yFlow.sharedEndpoint.switchId, true)
//        involvedSwitches.each { sw ->
            //TODO: new method signature after rebase
//            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty()
//            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty()
//        }

        cleanup:
        yFlow && !flowRemoved && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where:
        //Not all cases may be covered. Uncovered cases will be shown as a 'skipped' test
        data << getFLowsTestData()
        swT = data.swT as SwitchTriplet
        yFlowRequest = data.yFlow as YFlowCreatePayload
        coveredCases = data.coveredCases as List<String>
        trafficApplicable = isTrafficApplicable(swT)
        trafficDisclaimer = trafficApplicable ? " and pass traffic" : " [!NO TRAFFIC CHECK!]"
    }

    @Tidy
    def "System forbids to create a y-flow with conflict: #data.descr"(YFlowCreatePayload yFlow) {
        assumeTrue(data.yFlow != null, "This case cannot be covered on given topology: $data.descr")

        when: "Try creating a y-flow with one endpoint being in conflict with the other one"
        def yFlowResponse = yFlowHelper.addYFlow(yFlow)

        then: "Error is received, describing the problem"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Could not create y-flow"
            assertThat(errorDescription).matches(data.errorPattern(yFlow))
        }

        and: "'Get' y-flows returns no flows"
        northboundV2.getAllYFlows().empty

        and: "'Get' flows returns no flows"
        northboundV2.getAllFlows().empty

        cleanup:
        yFlowResponse && !exc && yFlowHelper.deleteYFlow(yFlowResponse.YFlowId)

        where: "Use different types of conflicts"
        data << [
                [
                        descr: "subflow1 and subflow2 same vlan on shared endpoint",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets[0]).tap {
                            it.subFlows[1].sharedEndpoint.vlanId = it.subFlows[0].sharedEndpoint.vlanId
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The sub-flows .* and .* have shared endpoint conflict: \
SubFlowSharedEndpointEncapsulation\(vlanId=${flow.subFlows[0].sharedEndpoint.vlanId}, innerVlanId=0\) \/ \
SubFlowSharedEndpointEncapsulation\(vlanId=${flow.subFlows[1].sharedEndpoint.vlanId}, innerVlanId=0\)/
                        }
                ],
                [
                        descr: "subflow1 and subflow2 no vlan on shared endpoint",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets[0]).tap {
                            it.subFlows[0].sharedEndpoint.vlanId = 0
                            it.subFlows[1].sharedEndpoint.vlanId = 0
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The sub-flows .*? and .*? have shared endpoint conflict: \
SubFlowSharedEndpointEncapsulation\(vlanId=0, innerVlanId=0\) \/ \
SubFlowSharedEndpointEncapsulation\(vlanId=0, innerVlanId=0\)/
                        }
                ],
                [
                        descr: "ep1 = ep2, same vlan",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets.find { it.ep1 == it.ep2 }).tap {
                            it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
                            it.subFlows[1].endpoint.vlanId = it.subFlows[0].endpoint.vlanId
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The sub-flows .*? and .*? have endpoint conflict: \
switchId="${flow.subFlows[0].endpoint.switchId}" port=${flow.subFlows[0].endpoint.portNumber} vlanId=${flow.subFlows[0].endpoint.vlanId} \/ \
switchId="${flow.subFlows[1].endpoint.switchId}" port=${flow.subFlows[1].endpoint.portNumber} vlanId=${flow.subFlows[1].endpoint.vlanId}/
                        }
                ],
                [
                        descr: "ep1 = ep2, both no vlan",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets.find { it.ep1 == it.ep2 }).tap {
                            it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
                            it.subFlows[0].endpoint.vlanId = 0
                            it.subFlows[1].endpoint.vlanId = 0
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The sub-flows .*? and .*? have endpoint conflict: \
switchId="${flow.subFlows[0].endpoint.switchId}" port=${flow.subFlows[0].endpoint.portNumber} \/ \
switchId="${flow.subFlows[1].endpoint.switchId}" port=${flow.subFlows[1].endpoint.portNumber}/
                        }
                ],
                [
                        descr: "ep1 = ep2, vlans [0,X] and [X,0]",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets.find { it.ep1 == it.ep2 }).tap {
                            it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
                            it.subFlows[0].endpoint.innerVlanId = it.subFlows[0].endpoint.vlanId
                            it.subFlows[0].endpoint.vlanId = 0
                            it.subFlows[1].endpoint.vlanId = it.subFlows[0].endpoint.innerVlanId
                            it.subFlows[1].endpoint.innerVlanId = 0
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The sub-flows .*? and .*? have endpoint conflict: \
switchId="${flow.subFlows[0].endpoint.switchId}" port=${flow.subFlows[0].endpoint.portNumber} vlanId=${flow.subFlows[0].endpoint.innerVlanId} \/ \
switchId="${flow.subFlows[1].endpoint.switchId}" port=${flow.subFlows[1].endpoint.portNumber} vlanId=${flow.subFlows[1].endpoint.vlanId}/
                        }
                ],
                [
                        descr: "ep1 on ISL port",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets[0]).tap {
                            def islPort = topology.getBusyPortsForSwitch(it.subFlows[0].endpoint.switchId)[0]
                            it.subFlows[0].endpoint.portNumber = islPort
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The port ${flow.subFlows[0].endpoint.portNumber} on the \
switch '${flow.subFlows[0].endpoint.switchId}' is occupied by an ISL \(destination endpoint collision\)./
                        }
                ],
                [
                        descr: "shared endpoint on ISL port",
                        yFlow: yFlowHelper.randomYFlow(topologyHelper.switchTriplets[0]).tap {
                            def islPort = topology.getBusyPortsForSwitch(it.sharedEndpoint.switchId)[0]
                            it.sharedEndpoint.portNumber = islPort
                        },
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/The port ${flow.sharedEndpoint.portNumber} on the \
switch '${flow.sharedEndpoint.switchId}' is occupied by an ISL \(source endpoint collision\)./
                        }
                ],
                [
                        descr: "ep2 on s42 port",
                        yFlow: {
                            def swTriplet = topologyHelper.getSwitchTriplets(true).find { it.ep2.prop?.server42Port }
                            if (swTriplet) {
                                return yFlowHelper.randomYFlow(swTriplet).tap {
                                    it.subFlows[1].endpoint.portNumber = swTriplet.ep2.prop.server42Port
                                }
                            }
                            return null
                        }(),
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/Server 42 port in the switch properties for switch '${flow.subFlows[1].endpoint.switchId}'\
 is set to '${flow.subFlows[1].endpoint.portNumber}'. It is not possible to create or update an endpoint with these parameters./
                        }
                ],
                [
                        descr: "shared endpoint on s42 port",
                        yFlow: {
                            def swTriplet = topologyHelper.switchTriplets.find { it.shared.prop?.server42Port }
                            if (swTriplet) {
                                return yFlowHelper.randomYFlow(swTriplet).tap {
                                    it.sharedEndpoint.portNumber = swTriplet.shared.prop.server42Port
                                }
                            }
                            return null
                        }(),
                        errorPattern: { YFlowCreatePayload flow ->
                            ~/Server 42 port in the switch properties for switch '${flow.sharedEndpoint.switchId}'\
 is set to '${flow.sharedEndpoint.portNumber}'. It is not possible to create or update an endpoint with these parameters./
                        }
                ],
                [
                        descr: "single-switch, shared = ep1",
                        yFlow: yFlowHelper.randomYFlow(topology.activeSwitches[0], topology.activeSwitches[0],
                                topology.activeSwitches[1]),
                        errorPattern: { YFlowCreatePayload flow ->
                            ~"It is not allowed to create one-switch y-flow"
                        }
                ],
                [
                        descr: "single-switch, shared = ep2",
                        yFlow: yFlowHelper.randomYFlow(topology.activeSwitches[0], topology.activeSwitches[1],
                                topology.activeSwitches[0]),
                        errorPattern: { YFlowCreatePayload flow ->
                            ~"It is not allowed to create one-switch y-flow"
                        }
                ]
        ]
        yFlow = data.yFlow as YFlowCreatePayload
    }

    @Tidy
    def "System forbids to create a y-flow with conflict: subflow1 vlans are [0,X] and subflow2 vlans are [X,0] on shared endpoint"() {
        when: "Try creating a y-flow with one endpoint being in conflict with the other one"
        def flow = yFlowHelper.randomYFlow(topologyHelper.switchTriplets[0]).tap {
            it.subFlows[0].sharedEndpoint.innerVlanId = it.subFlows[0].sharedEndpoint.vlanId
            it.subFlows[0].sharedEndpoint.vlanId = 0
            it.subFlows[1].sharedEndpoint.vlanId = it.subFlows[0].sharedEndpoint.innerVlanId
            it.subFlows[1].sharedEndpoint.innerVlanId = 0
        }
        def yFlowResponse = yFlowHelper.addYFlow(flow)

        then: "Error is received, describing the problem"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.CONFLICT
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Could not create y-flow"
            assertThat(errorDescription).matches(~/FlowValidateAction failed: \
Requested flow '.*?' conflicts with existing flow '.*?'. Details: requested flow '.*?' \
source: switchId="${flow.sharedEndpoint.switchId}" port=${flow.sharedEndpoint.portNumber} vlanId=${flow.subFlows[0].sharedEndpoint.innerVlanId}, \
existing flow '.*?' \
source: switchId="${flow.sharedEndpoint.switchId}" port=${flow.sharedEndpoint.portNumber} vlanId=${flow.subFlows[1].sharedEndpoint.vlanId}/)
        }

        and: "'Get' y-flows returns no flows"
        Wrappers.wait(WAIT_OFFSET) { //even on error system briefly creates an 'in progress' flow
            assert northboundV2.getAllYFlows().empty
        }

        and: "'Get' flows returns no flows"
        northboundV2.getAllFlows().empty

        cleanup:
        yFlowResponse && !exc && yFlowHelper.deleteYFlow(yFlowResponse.YFlowId)
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "System forbids to create a y-flow with conflict: shared endpoint port is inside a LAG group"() {
        given: "A LAG port"
        def swT = topologyHelper.switchTriplets.find { it.shared.features.contains(SwitchFeature.LAG) }
        assumeTrue(swT != null, "Unable to find a switch that supports LAG")
        def portsArray = topology.getAllowedPortsForSwitch(swT.shared)[-2, -1]
        def payload = new CreateLagPortDto(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(swT.shared.dpId, payload).logicalPortNumber

        when: "Try creating a y-flow with shared endpoint port being inside LAG"
        def yFlow = yFlowHelper.randomYFlow(swT).tap {
            it.sharedEndpoint.portNumber = portsArray[0]
        }
        def yFlowResponse = yFlowHelper.addYFlow(yFlow)

        then: "Error is received, describing the problem"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Could not create y-flow"
            assert errorDescription == "Port ${portsArray[0]} on switch $swT.shared.dpId is used " +
                    "as part of LAG port $lagPort"
        }

        and: "'Get' y-flows returns no flows"
        northboundV2.getAllYFlows().empty

        and: "'Get' flows returns no flows"
        northboundV2.getAllFlows().empty

        cleanup:
        yFlowResponse && !exc && yFlowHelper.deleteYFlow(yFlowResponse.YFlowId)
        lagPort && northboundV2.deleteLagLogicalPort(swT.shared.dpId, lagPort)
    }

    /**
     * First N iterations are covering all unique se-yp-ep combinations from 'getSwTripletsTestData' with random vlans.
     * Then add required iterations of unusual vlan combinations (default port, qinq, etc.)
     */
    def getFLowsTestData() {
        List<Map> testData = getSwTripletsTestData()
        //random vlans on getSwTripletsTestData
        testData.findAll { it.swT != null }.each {
            it.yFlow = yFlowHelper.randomYFlow(it.swT)
            it.coveredCases << "random vlans"
        }
        //se noVlan+vlan, ep1-ep2 same sw-port, vlan+noVlan
        testData.with {
            def suitingTriplets = owner.topologyHelper.switchTriplets.findAll { it.ep1 == it.ep2 }
            def swT = suitingTriplets.find { isTrafficApplicable(it) } ?: suitingTriplets[0]
            def yFlow = owner.yFlowHelper.randomYFlow(swT).tap {
                it.subFlows[0].sharedEndpoint.vlanId = 0
                it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
                it.subFlows[1].endpoint.vlanId = 0
            }
            add([swT: swT, yFlow: yFlow, coveredCases: ["se noVlan+vlan, ep1-ep2 same sw-port, vlan+noVlan"]])
        }
        //se qinq, ep1 default, ep2 qinq
        testData.with {
            def suitingTriplets = owner.topologyHelper.switchTriplets.findAll { it.ep1 != it.ep2 }
            def swT = suitingTriplets.find { isTrafficApplicable(it) } ?: suitingTriplets[0]
            def yFlow = owner.yFlowHelper.randomYFlow(swT).tap {
                it.subFlows[1].sharedEndpoint.vlanId = it.subFlows[0].sharedEndpoint.vlanId
                it.subFlows[0].sharedEndpoint.innerVlanId = 11
                it.subFlows[1].sharedEndpoint.innerVlanId = 22
                it.subFlows[0].endpoint.vlanId = 0
                it.subFlows[1].endpoint.innerVlanId = 11
            }
            add([swT: swT, yFlow: yFlow, coveredCases: ["se qinq, ep1 default, ep2 qinq"]])
        }
        //se qinq, ep1-ep2 same sw-port, qinq
        testData.with {
            def suitingTriplets = owner.topologyHelper.switchTriplets.findAll { it.ep1 == it.ep2 }
            def swT = suitingTriplets.find { isTrafficApplicable(it) } ?: suitingTriplets[0]
            def yFlow = owner.yFlowHelper.randomYFlow(swT).tap {
                it.subFlows[0].sharedEndpoint.vlanId = 123
                it.subFlows[1].sharedEndpoint.vlanId = 124
                it.subFlows[0].sharedEndpoint.innerVlanId = 111
                it.subFlows[1].sharedEndpoint.innerVlanId = 111
                it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
                it.subFlows[0].endpoint.vlanId = 222
                it.subFlows[1].endpoint.vlanId = 222
                it.subFlows[0].endpoint.innerVlanId = 333
                it.subFlows[1].endpoint.innerVlanId = 444
            }
            add([swT: swT, yFlow: yFlow, coveredCases: ["se qinq, ep1-ep2 same sw-port, qinq"]])
        }
        return testData
    }

    def getSwTripletsTestData() {
        def requiredCases = [
                //se = shared endpoint, ep = subflow endpoint, yp = y-point
                [name     : "se is wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared }],
                [name     : "se is non-wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared }],
                [name     : "ep on wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "ep on non-wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> !swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "se+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared }],
                [name     : "se+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared }],
                [name     : "yp on wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared && yPoints[0] != swT.ep1 && yPoints[0] != swT.ep2 }],
                [name     : "yp on non-wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared && yPoints[0] != swT.ep1 && yPoints[0] != swT.ep2 }],
                [name     : "ep+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1 || yPoints[0] == swT.ep2) }],
                [name     : "ep+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1 || yPoints[0] == swT.ep2) }]
        ]
        requiredCases.each { it.picked = false }
        //match all triplets to the list of requirements that it satisfies
        Map<SwitchTriplet, List<String>> weightedTriplets =  topologyHelper.switchTriplets.collectEntries { triplet ->
            [(triplet): requiredCases.findAll { it.condition(triplet) }*.name ]
        }
        //sort, so that most valuable triplet is first
        weightedTriplets = weightedTriplets.sort { - it.value.size() }
        def result = []
        //greedy alg. Pick most valuable triplet. Re-weigh remaining triplets considering what is no longer required and repeat
        while(requiredCases.find { !it.picked } && weightedTriplets.entrySet()[0].value.size() > 0) {
            def pick = weightedTriplets.entrySet()[0]
            weightedTriplets.remove(pick.key)
            pick.value.each {satisfiedCase ->
                requiredCases.find{ it.name == satisfiedCase}.picked = true
            }
            weightedTriplets.entrySet().each { it.value.removeAll(pick.value) }
            weightedTriplets = weightedTriplets.sort { - it.value.size() }
            result << [swT: pick.key, coveredCases: pick.value]
        }
        def notPicked = requiredCases.findAll { !it.picked }
        if (notPicked) {
            //special entry, passing cases that are not covered for later processing
            result << [swT: null, coveredCases: notPicked*.name]
        }
        return result
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

    static boolean isTrafficApplicable(SwitchTriplet swT) {
        swT ? [swT.shared, swT.ep1, swT.ep2].every { it.traffGens } : false
    }
}
