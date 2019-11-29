package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Unroll

import javax.inject.Provider

@Ignore("https://github.com/telstra/open-kilda/issues/2971")
class QinQFlowSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Unroll
    def "System allows to manipulate with QinQ flow\
(srcVlanId: #srcVlanId, srcInnerVlanId: #srcInnerVlanId, dstVlanId: #dstVlanId, dstInnerVlanId: #dstInnerVlanId)"() {
        given: "Two switches connected to traffgen and enabled multiTable mode"
        def allTraffGenSwitches = topology.activeTraffGens*.switchConnected
        assumeTrue("Unable to find required switches in topology", (allTraffGenSwitches.size() > 1))
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { sw ->
                sw.dpId in allTraffGenSwitches*.dpId && northbound.getSwitchProperties(sw.dpId).multiTable
            } && it.paths.size() > 2
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create a protected QinQ flow"
        def flow = flowHelperV2.randomFlow(swP)
        flow.source.vlanId = srcVlanId
        flow.source.innerVlanId = srcInnerVlanId
        flow.destination.vlanId = dstVlanId
        flow.destination.innerVlanId = dstInnerVlanId
        flow.allocateProtectedPath = true
        def response = flowHelperV2.addFlow(flow)

        then: "Response contains correct info about innerVlanIds"
        /** System doesn't allow to create a flow with innerVlan and without vlan at the same time.
         * for e.g.: when you create a flow with the following params:
         * vlan == 0 and innerVlan != 0,
         * then flow will be created with vlan != 0 and innerVlan == 0
         */
        with(response) {
            it.source.vlanId == (srcVlanId ? srcVlanId : srcInnerVlanId)
            it.source.innerVlanId == (srcVlanId ? srcInnerVlanId : 0)
            it.destination.vlanId == (dstVlanId ? dstVlanId : dstInnerVlanId)
            it.destination.innerVlanId == (dstVlanId ? dstInnerVlanId : 0)
        }

        and: "Flow is really created with requested innerVlanIds"
        with(northbound.getFlow(flow.flowId)) {
            it.source.vlanId == (srcVlanId ? srcVlanId : srcInnerVlanId)
            it.source.innerVlanId == (srcVlanId ? srcInnerVlanId : 0)
            it.destination.vlanId == (dstVlanId ? dstVlanId : dstInnerVlanId)
            it.destination.innerVlanId == (dstVlanId ? dstInnerVlanId : 0)
        }

        and: "Flow is valid and pingable"
        // is not implemented yet
//        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "The flow allows traffic"
        def traffExam = traffExamProvider.get()
        def examQinQFlow = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flowHelperV2.toV1(flow), 1000, 5)
        withPool {
            [examQinQFlow.forward, examQinQFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        // is not implemented yet
//        and: "Involved switches pass switch validation"
//        def involvedSwitchesFlow1 = pathHelper.getInvolvedSwitches(
//                pathHelper.convert(northbound.getFlowPath(flow.flowId))
//        )
//        involvedSwitchesFlow1.each {
//            with(northbound.validateSwitch(it.dpId)) { validation ->
//                validation.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
//                validation.verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
//            }
//        }

        when: "Create a flow without QinQ on the same port"
        def flow2 = flowHelper.randomFlow(swP).tap {
            it.source.portNumber = flow.source.portNumber
            it.source.vlanId = flow.source.vlanId + 1
            it.destination.portNumber = flow.destination.portNumber
            it.destination.vlanId = flow.destination.vlanId + 1
        }
        flowHelper.addFlow(flow2)

//        then: "Both existing flows are valid"
//        [flow.flowId, flow2.id].each {
//            northbound.validateFlow(it).each { assert it.asExpected }
//        }

        // is not implemented yet
//        and: "Involved switches pass switch validation"
//        def involvedSwitchesFlow2 = pathHelper.getInvolvedSwitches(pathHelper.convert(northbound.getFlowPath(flow2.id)))
//        def involvedSwitchesforBothFlows = (involvedSwitchesFlow1 + involvedSwitchesFlow2).unique { it.dpId }
//        involvedSwitchesforBothFlows.each {
//            with(northbound.validateSwitch(it.dpId)) { validation ->
//                validation.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
//                validation.verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
//            }
//        }

//        and: "Both flows are pingable"
//        [flow.flowId, flow2.id].each {
//            verifyAll(northbound.pingFlow(it, new PingInput())) {
//                it.forward.pingSuccess
//                it.reverse.pingSuccess
//            }
//        }

        then: "Both flows allow traffic"
        def examSimpleFlow = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flow2, 1000, 5)
        withPool {
            [examQinQFlow.forward, examQinQFlow.reverse, examSimpleFlow.forward, examSimpleFlow.reverse]
                    .eachParallel { direction ->
                        def resources = traffExam.startExam(direction)
                        direction.setResources(resources)
                        assert traffExam.waitExam(direction).hasTraffic()
                    }
        }

        when: "Update the QinQ flow"
        def newSrcInnerVlanId = srcInnerVlanId + 1
        def newDstInnerVlanId = dstInnerVlanId + 1
        def updateResponse = flowHelperV2.updateFlow(flow.flowId, flow.tap {
            flow.source.innerVlanId = newSrcInnerVlanId
            flow.destination.innerVlanId = newDstInnerVlanId
        })

        then: "Update response contains correct info about innerVlanIds"
        with(updateResponse) {
            it.source.innerVlanId == newSrcInnerVlanId
            it.destination.innerVlanId == newDstInnerVlanId
        }

        and: "Flow is really updated"
        with(northbound.getFlow(flow.flowId)) {
            it.source.innerVlanId == newSrcInnerVlanId
            it.destination.innerVlanId == newDstInnerVlanId
        }

        // is not implemented yet
//        then: "Both existing flows are still valid and pingable"
//        [flow.flowId, flow2.id].each {
//            northbound.validateFlow(it).each { assert it.asExpected }
//        }
//        [flow.flowId, flow2.id].each {
//            verifyAll(northbound.pingFlow(it, new PingInput())) {
//                it.forward.pingSuccess
//                it.reverse.pingSuccess
//            }
//        }

        when: "Delete the flows"
        flowHelperV2.deleteFlow(flow.flowId)
        flowHelper.deleteFlow(flow2.id)

        then: "Flows rules are deleted"
        //is not implemented yet
//        involvedSwitchesforBothFlows.each { sw ->
//            Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
//                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
//            }
//        }


        where:
        srcVlanId | srcInnerVlanId | dstVlanId | dstInnerVlanId
        10        | 20             | 30        | 40
        10        | 20             | 30        | 0
        10        | 20             | 0         | 0
        10        | 0              | 0         | 40
        0         | 20             | 30        | 0
    }

    @Unroll
    def "System allows to create a single switch QinQ flow\
(srcVlanId: #srcVlanId, srcInnerVlanId: #srcInnerVlanId, dstVlanId: #dstVlanId, dstInnerVlanId: #dstInnerVlanId)"() {
        given: "A switch with enabled multiTable mode"
        def sw = topology.activeSwitches.find { northbound.getSwitchProperties(it.dpId).multiTable } ?:
                assumeTrue("No suiting switches found", false)

        when: "Create a single switch QinQ flow"
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flow.source.vlanId = srcVlanId
        flow.source.innerVlanId = srcInnerVlanId
        flow.destination.vlanId = dstVlanId
        flow.destination.innerVlanId = dstInnerVlanId
        def response = flowHelperV2.addFlow(flow)

        then: "Response contains correct info about innerVlanIds"
        with(response) {
            it.source.vlanId == (srcVlanId ? srcVlanId : srcInnerVlanId)
            it.source.innerVlanId == (srcVlanId ? srcInnerVlanId : 0)
            it.destination.vlanId == (dstVlanId ? dstVlanId : dstInnerVlanId)
            it.destination.innerVlanId == (dstVlanId ? dstInnerVlanId : 0)
        }

        and: "Flow is really created with requested innerVlanIds"
        with(northbound.getFlow(flow.flowId)) {
            it.source.vlanId == (srcVlanId ? srcVlanId : srcInnerVlanId)
            it.source.innerVlanId == (srcVlanId ? srcInnerVlanId : 0)
            it.destination.vlanId == (dstVlanId ? dstVlanId : dstInnerVlanId)
            it.destination.innerVlanId == (dstVlanId ? dstInnerVlanId : 0)
        }

        and: "Flow is valid and pingable"
        //is not implemented yet
//        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        //is not implemented yet
//        and: "Involved switches pass switch validation"
//        with(pathHelper.getInvolvedSwitches(pathHelper.convert(northbound.getFlowPath(flow.flowId)))) {
//            def validationInfo = northbound.validateSwitch(it.dpId)
//            validationInfo.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
//            validationInfo.verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
//        }

        when: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "Flow rules are deleted"
        //is not implemented yet
//        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
//            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
//        }

        where:
        srcVlanId | srcInnerVlanId | dstVlanId | dstInnerVlanId
        10        | 20             | 30        | 40
        10        | 20             | 30        | 0
        10        | 20             | 0         | 0
        10        | 0              | 0         | 40
        0         | 20             | 30        | 0
    }

    @Tags(TOPOLOGY_DEPENDENT)
    def "System doesn't allow to create a QinQ flow when a switch doesn't support multi table mode"() {
        given: "A switch pair with disabled multi table mode on the src switch"
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].any { northbound.getSwitchProperties(it.dpId).multiTable }
        } ?: assumeTrue("No suiting switches found", false)
        def initSrcSwProps = northbound.getSwitchProperties(swP.src.dpId)
        northbound.updateSwitchProperties(swP.src.dpId,
                northbound.getSwitchProperties(swP.src.dpId).tap { multiTable = false })
        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
            assert northbound.getSwitchRules(swP.src.dpId).flowEntries*.cookie.sort() == swP.src.defaultCookies.sort()
        }
        when: "Try to create a QinQ flow when at least on switch doesn't support multi table mode"

        def flow = flowHelperV2.randomFlow(swP)
        flow.source.innerVlanId = 4093
        flow.destination.innerVlanId = 3904
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == "Could not create flow"

        cleanup: "Revert system to original state"
        northbound.updateSwitchProperties(swP.src.dpId, initSrcSwProps)
        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
            assert northbound.getSwitchRules(swP.src.dpId).flowEntries*.cookie.sort() == swP.src.defaultCookies.sort()
        }
    }

    @Unroll
    def "System doesn't allow to create a QinQ flow with incorrect innerVlanIds\
(src:#srcInnerVlanId, dst:#dstInnerVlanId)"() {
        when: "Try to create a QinQ flow with incorrect innerVlanId"
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].any { northbound.getSwitchProperties(it.dpId).multiTable }
        } ?: assumeTrue("No suiting switches found", false)

        def flow = flowHelperV2.randomFlow(swP)
        flow.source.innerVlanId = srcInnerVlanId
        flow.destination.innerVlanId = dstInnerVlanId
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == "Invalid request payload"

        where:
        srcInnerVlanId | dstInnerVlanId
        4096           | 10
        10             | -1
    }

    def "System doesn't allow to create a QinQ flow via APIv1"() {
        given: "Two switches with enabled multi table mode"
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { northbound.getSwitchProperties(it.dpId).multiTable }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create a QinQ flow via APIv1"
        def flowV1 = flowHelper.randomFlow(swP)
        flowV1.source.innerVlanId = 100
        flowV1.destination.innerVlanId = 200
        northbound.addFlow(flowV1)

        then: "Human readable error is returned"
        def eCreate = thrown(HttpClientErrorException)
        eCreate.statusCode == HttpStatus.BAD_REQUEST
        eCreate.responseBodyAsString.to(MessageError).errorMessage.contains("Could not create flow")
    }

    def "System doesn't allow to update/delete a QinQ flow via APIv1"() {
        given: "Two switches with enabled multi table mode"
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { northbound.getSwitchProperties(it.dpId).multiTable }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create a QinQ flow via APIv2"
        def flowV2 = flowHelperV2.randomFlow(swP)
        flowV2.source.innerVlanId = 234
        flowV2.destination.innerVlanId = 432
        northboundV2.addFlow(flowV2)

        and: "Try to update the flow via APIv1"
        flowHelperV2.updateFlow(flowV2.flowId, flowV2.tap { flowV2.source.innerVlanId += 1 })

        then: "Human readable error is returned"
        def eUpdate = thrown(HttpClientErrorException)
        eUpdate.statusCode == HttpStatus.BAD_REQUEST
        eUpdate.responseBodyAsString.to(MessageError).errorMessage.contains("Could not update flow")

        when: "Try to delete the flow via APIv1"
        northbound.deleteFlow(flowV2.flowId)

        then: "Human readable error is returned"
        def eDelete = thrown(HttpClientErrorException)
        eDelete.statusCode == HttpStatus.BAD_REQUEST
        eDelete.responseBodyAsString.to(MessageError).errorMessage.contains("Could not delete flow")

        cleanup:
        flowV2 && flowHelperV2.deleteFlow(flowV2.flowId)
    }

    def "System allows to create QinQ flow and flow without QnQ with the same vlan on the same port"() {
        given: "Two switches with enabled multi table mode"
        def allTraffGenSwitches = topology.activeTraffGens*.switchConnected
        assumeTrue("Unable to find required switches in topology", (allTraffGenSwitches.size() > 1))
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { sw ->
                sw.dpId in allTraffGenSwitches*.dpId && northbound.getSwitchProperties(sw.dpId).multiTable
            }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create a QinQ flow"
        def flowWithQinQ = flowHelperV2.randomFlow(swP)
        flowWithQinQ.source.innerVlanId = 234
        flowWithQinQ.destination.innerVlanId = 432
        flowHelperV2.addFlow(flowWithQinQ)

        and: "Create a flow without QinQ"
        def flowWithoutQinQ = flowHelperV2.randomFlow(swP)
        flowWithoutQinQ.source.vlanId = 0
        flowWithoutQinQ.source.innerVlanId = flowWithQinQ.source.vlanId
        flowHelperV2.addFlow(flowWithoutQinQ)

        then: "Both flows allow traffic"
        def traffExam = traffExamProvider.get()
        def examFlowWithtQinQ = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flowHelperV2.toV1(flowWithQinQ), 1000, 5)
        def examFlowWithoutQinQ = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flowHelperV2.toV1(flowWithoutQinQ), 1000, 5)
        withPool {
            [examFlowWithtQinQ.forward, examFlowWithtQinQ.reverse,
             examFlowWithoutQinQ.forward, examFlowWithoutQinQ.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Revert system to original state"
        [flowWithQinQ, flowWithoutQinQ].each { flowHelperV2.deleteFlow(it.flowId) }
    }

    def "System detects conflict QinQ flows"() {
        given: "Two switches with enabled multi table mode"
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { northbound.getSwitchProperties(it.dpId).multiTable }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create a first flow"
        def flow1 = flowHelperV2.randomFlow(swP)
        flow1.source.vlanId = 10
        flow1.source.innerVlanId = 0
        flowHelperV2.addFlow(flow1)

        and: "Try to create a conflict flow"
        def flow2 = flowHelperV2.randomFlow(swP)
        flow2.source.vlanId = 0
        flow2.source.innerVlanId = flow1.source.vlanId
        northboundV2.addFlow(flow2)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.CONFLICT
        exc.responseBodyAsString.to(MessageError).errorMessage == "Could not create flow"

        and: "Revert system to original state"
        flowHelperV2.deleteFlow(flow1.flowId)
    }

    @Unroll
    def "System allows to create a single-switch-port QinQ flow\
(srcVlanId: #srcVlanId, srcInnerVlanId: #srcInnerVlanId, dstVlanId: #dstVlanId, dstInnerVlanId: #dstInnerVlanId)"() {
        given: "A switch with enabled multiTable mode"
        def sw = topology.activeSwitches.find { northbound.getSwitchProperties(it.dpId).multiTable } ?:
                assumeTrue("No suiting switches found", false)

        when: "Create a single switch QinQ flow"
        def flow = flowHelperV2.singleSwitchSinglePortFlow(sw)
        flow.source.vlanId = srcVlanId
        flow.source.innerVlanId = srcInnerVlanId
        flow.destination.vlanId = dstVlanId
        flow.destination.innerVlanId = dstInnerVlanId
        def response = flowHelperV2.addFlow(flow)

        then: "Response contains correct info about innerVlanIds"
        with(response) {
            it.source.vlanId == (srcVlanId ? srcVlanId : srcInnerVlanId)
            it.source.innerVlanId == (srcVlanId ? srcInnerVlanId : 0)
            it.destination.vlanId == (dstVlanId ? dstVlanId : dstInnerVlanId)
            it.destination.innerVlanId == (dstVlanId ? dstInnerVlanId : 0)
        }

        and: "Flow is really created with requested innerVlanIds"
        with(northbound.getFlow(flow.flowId)) {
            it.source.vlanId == (srcVlanId ? srcVlanId : srcInnerVlanId)
            it.source.innerVlanId == (srcVlanId ? srcInnerVlanId : 0)
            it.destination.vlanId == (dstVlanId ? dstVlanId : dstInnerVlanId)
            it.destination.innerVlanId == (dstVlanId ? dstInnerVlanId : 0)
        }

        and: "Flow is valid and pingable"
        //is not implemented yet
//        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        //is not implemented yet
//        and: "Involved switches pass switch validation"
//        with(pathHelper.getInvolvedSwitches(pathHelper.convert(northbound.getFlowPath(flow.flowId)))) {
//            def validationInfo = northbound.validateSwitch(it.dpId)
//            validationInfo.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
//            validationInfo.verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
//        }

        when: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "Flow rules are deleted"
        //is not implemented yet
//        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
//            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
//        }

        where:
        srcVlanId | srcInnerVlanId | dstVlanId | dstInnerVlanId
        10        | 20             | 30        | 40
        10        | 20             | 30        | 0
        10        | 20             | 0         | 0
        10        | 0              | 0         | 40
        0         | 20             | 30        | 0
    }
}
