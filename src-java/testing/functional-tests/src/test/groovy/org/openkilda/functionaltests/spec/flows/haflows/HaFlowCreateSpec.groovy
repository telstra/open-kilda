package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.AssertionsForClassTypes.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify create operations on ha-flows.")
class HaFlowCreateSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid ha-flow can be created [!NO TRAFFIC CHECK!], covered cases: #coveredCases"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        assumeTrue(swT != null, "These cases cannot be covered on given topology: $coveredCases")
        if (coveredCases.toString().contains("qinq")) {
            assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        }

        when: "Create a ha-flow of certain configuration"
        def haFlow = northboundV2.addHaFlow(haFlowRequest)

        then: "Y-flow is created and has UP status"
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            haFlow = northboundV2.getHaFlow(haFlow.haFlowId)
            assert haFlow && haFlow.status == FlowState.UP.toString()
        }

        when: "Delete the ha-flow"
        northboundV2.deleteHaFlow(haFlow.haFlowId)

        then: "The ha-flow is no longer visible via 'get' API"
        Wrappers.wait(WAIT_OFFSET) { assert !northboundV2.getHaFlow(haFlow.haFlowId) }
        def flowRemoved = true

        and: "And involved switches pass validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(haFlow).eachParallel { swId ->
                assert northboundV2.validateSwitch(swId).isAsExpected()
            }
        }

        cleanup:
        haFlow && !flowRemoved && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where:
        //Not all cases may be covered. Uncovered cases will be shown as a 'skipped' test
        data << getFlowsTestData()
        swT = data.swT as SwitchTriplet
        haFlowRequest = data.haFlow as HaFlowCreatePayload
        coveredCases = data.coveredCases as List<String>
    }

    @Tidy
    def "User cannot create a ha-flow with existent ha_flow_id"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "Try to create the same ha-flow"
        haFlowRequest.haFlowId = haFlow.haFlowId
        haFlowHelper.addHaFlow(haFlowRequest)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.CONFLICT
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Could not create ha-flow"
            assertThat(errorDescription).matches(/HA-flow .*? already exists/)
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tidy
    def "User cannot create a one switch ha-flow"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "A switch"
        def sw = topologyHelper.getRandomSwitch()

        when: "Try to create one switch ha-flow"
        def haFlowRequest = haFlowHelper.singleSwitchHaFlow(sw)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Could not create ha-flow"
            assertThat(errorDescription).matches(/The ha-flow .*? is one switch flow\..*?/)
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    /**
     * First N iterations are covering all unique se-yp-ep combinations from 'getSwTripletsTestData' with random vlans.
     * Then add required iterations of unusual vlan combinations (default port, qinq, etc.)
     */
    def getFlowsTestData() {
        List<Map> testData = getSwTripletsTestData()
        //random vlans on getSwTripletsTestData
        testData.findAll { it.swT != null }.each {
            it.haFlow = haFlowHelper.randomHaFlow(it.swT)
            it.coveredCases << "random vlans"
        }
        //se noVlan, ep1-ep2 same sw-port, vlan+vlan
        testData.with {
            def suitingTriplets = owner.topologyHelper.switchTriplets.findAll { it.ep1 == it.ep2 }
            def swT = suitingTriplets[0]
            def haFlow = owner.haFlowHelper.randomHaFlow(swT).tap {
                it.sharedEndpoint.vlanId = 0
                it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
            }
            add([swT: swT, haFlow: haFlow, coveredCases: ["noVlan, ep1-ep2 same sw-port, vlan+vlan"]])
        }
        //se qinq, ep1 default, ep2 qinq
        testData.with {
            def suitingTriplets = owner.topologyHelper.switchTriplets.findAll { it.ep1 != it.ep2 }
            def swT = suitingTriplets[0]
            def haFlow = owner.haFlowHelper.randomHaFlow(swT).tap {
                it.sharedEndpoint.vlanId = 11
                it.sharedEndpoint.innerVlanId = 22
                it.subFlows[0].endpoint.vlanId = 0
                it.subFlows[1].endpoint.innerVlanId = 11
            }
            add([swT: swT, haFlow: haFlow, coveredCases: ["se qinq, ep1 default, ep2 qinq"]])
        }
        //se qinq, ep1-ep2 same sw-port, qinq
        testData.with {
            def suitingTriplets = owner.topologyHelper.switchTriplets.findAll { it.ep1 == it.ep2 }
            def swT = suitingTriplets[0]
            def haFlow = owner.haFlowHelper.randomHaFlow(swT).tap {
                it.sharedEndpoint.vlanId = 123
                it.sharedEndpoint.innerVlanId = 124
                it.subFlows[1].endpoint.portNumber = it.subFlows[0].endpoint.portNumber
                it.subFlows[0].endpoint.vlanId = 222
                it.subFlows[1].endpoint.vlanId = 222
                it.subFlows[0].endpoint.innerVlanId = 333
                it.subFlows[1].endpoint.innerVlanId = 444
            }
            add([swT: swT, haFlow: haFlow, coveredCases: ["se qinq, ep1-ep2 same sw-port, qinq"]])
        }
        return testData
    }

    def getSwTripletsTestData() {
        def requiredCases = [
                //se = shared endpoint, ep = subflow endpoint, yp = y-point
                [name     : "se is wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared
                 }],
                [name     : "se is non-wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared
                 }],
                [name     : "ep on wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "ep on non-wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> !swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "se+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared
                 }],
                [name     : "se+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared
                 }],
                [name     : "yp on wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared && yPoints[0] != swT.ep1 && yPoints[0] != swT.ep2
                 }],
                [name     : "yp on non-wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared && yPoints[0] != swT.ep1 && yPoints[0] != swT.ep2
                 }],
                [name     : "ep+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1 || yPoints[0] == swT.ep2)
                 }],
                [name     : "ep+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1 || yPoints[0] == swT.ep2)
                 }],
                [name     : "yp==se",
                 condition: { SwitchTriplet swT ->
                     def yPoints = yFlowHelper.findPotentialYPoints(swT)
                     yPoints.size() == 1 && yPoints[0] == swT.shared && swT.shared != swT.ep1 && swT.shared != swT.ep2
                 }]
        ]
        requiredCases.each { it.picked = false }
        //match all triplets to the list of requirements that it satisfies
        Map<SwitchTriplet, List<String>> weightedTriplets = topologyHelper.getSwitchTriplets(false, true)
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
}
