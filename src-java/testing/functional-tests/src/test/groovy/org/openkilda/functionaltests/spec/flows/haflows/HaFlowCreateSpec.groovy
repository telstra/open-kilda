package org.openkilda.functionaltests.spec.flows.haflows

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.haflow.HaFlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.haflow.HaFlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowFactory
import org.openkilda.functionaltests.helpers.builder.HaFlowBuilder
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.testing.service.traffexam.TraffExamService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import jakarta.inject.Provider

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.model.SwitchTriplet.ONE_SWITCH_FLOW

@Slf4j
@Narrative("Verify create operations on HA-Flows.")
@Tags([HA_FLOW])
class HaFlowCreateSpec extends HealthCheckSpecification {


    @Autowired @Shared
    HaFlowFactory haFlowFactory

    @Autowired @Shared
    Provider<TraffExamService> traffExamProvider

    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid HA-Flow can be created#trafficDisclaimer, covered cases: #coveredCases"() {
        assumeTrue(swT != null, "These cases cannot be covered on given topology: $coveredCases")

        when: "Create an HA-Flow of certain configuration"
        def haFlow = haFlowBuilder.build().create()
        def haFlowPath = haFlow.retrievedAllEntityPaths()

        def subFlowsPaths = haFlowPath.subFlowPaths.collect { it.path.forward.nodes.toPathNode() }
        boolean isOneSubFlowEndpointYPoint = subFlowsPaths.first().size() > subFlowsPaths.last().size() ?
                subFlowsPaths.first().containsAll(subFlowsPaths.last()) : subFlowsPaths.last().containsAll(subFlowsPaths.first())

        then: "Traffic passes through HA-Flow"
        if (swT.isHaTraffExamAvailable()) {
            assert haFlow.traffExam(traffExamProvider.get()).run().hasTraffic()
        }

        and: "HA-flow pass validation"
        haFlow.validate().asExpected

        and: "HA-Flow is pingable"
        // Ping operation is temporary allowed only for multi switch HA-Flows https://github.com/telstra/open-kilda/issues/5224
        //Ping operation is temporary disabled when one of the sub-flows' ends is Y-Point https://github.com/telstra/open-kilda/pull/5381
        if (SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(swT) && !isOneSubFlowEndpointYPoint) {
            def response = haFlow.ping(2000)
            assert !response.error
            response.subFlows.each {
                assert it.forward.pingSuccess
                assert it.reverse.pingSuccess
            }
        }

        and: "HA-Flow has been successfully deleted"
        def flowRemoved = haFlow.delete()

        and: "And involved switches pass validation"
        def involvedSwitchIds = haFlowPath.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()

        where:
        //Not all cases may be covered. Uncovered cases will be shown as a 'skipped' test
        data << getFlowsTestData()
        swT = data.swT as SwitchTriplet
        haFlowBuilder = data.haFlowBuilder as HaFlowBuilder
        coveredCases = data.coveredCases as List<String>
        trafficDisclaimer = swT && swT.isHaTraffExamAvailable() ? " and pass traffic" : " [!NO TRAFFIC CHECK!]"
    }

    def "User cannot create an HA-Flow with existent ha_flow_id"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = haFlowFactory.getRandom(swT)

        when: "Try to create the same HA-Flow"
        haFlowFactory.getBuilder(swT, false, haFlow.occupiedEndpoints())
                .withHaFlowId(haFlow.haFlowId).build().create()

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotCreatedWithConflictExpectedError(~/HA-flow ${haFlow.getHaFlowId()} already exists/).matches(exc)
    }

    def "User cannot create an HA-Flow with equal A-B endpoints and different inner vlans at these endpoints"() {
        given: "A switch triplet with equal A-B endpoint switches"
        def swT = topologyHelper.switchTriplets[0]
        def iShapedSwitchTriplet = new SwitchTriplet(swT.shared, swT.ep1, swT.ep1, swT.pathsEp1, swT.pathsEp1)

        when: "Try to create I-shaped HA-Flow request with equal A-B endpoint switches and different innerVlans"
        def haFlowInvalidRequest = haFlowFactory.getBuilder(iShapedSwitchTriplet)
                .withEp1Vlan(1).withEp1QnQ(2)
                .withEp2Vlan(3).withEp2QnQ(4)
        .build()
        haFlowInvalidRequest.create()

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotCreatedExpectedError(~/To have ability to use double vlan tagging for both sub flow \
destination endpoints which are placed on one switch .*? you must set equal inner vlan for both endpoints. \
Current inner vlans: ${haFlowInvalidRequest.subFlows[0].endpointInnerVlan} \
and ${haFlowInvalidRequest.subFlows[1].endpointInnerVlan}./).matches(exc)
    }

    def "User cannot create a one switch HA-Flow"() {
        given: "A switch"
        def swT = topologyHelper.getSwitchTriplets(false, true).find(ONE_SWITCH_FLOW)

        when: "Try to create one switch HA-Flow"
        haFlowFactory.getBuilder(swT).build().create()

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotCreatedExpectedError(~/The ha-flow .*? is one switch flow/).matches(exc)
    }

    /**
     * First N iterations are covering all unique se-yp-ep combinations from 'getSwTripletsTestData' with random vlans.
     * Then add required iterations of unusual vlan combinations (default port, qinq, etc.)
     */
    def getFlowsTestData() {
        List<Map> testData = getSwTripletsTestData()

        //random vlans on getSwTripletsTestData
        testData.findAll { it.swT != null }.each {
            it.haFlowBuilder = owner.haFlowFactory.getBuilder(it.swT)
            it.coveredCases << "random vlans"
        }
        //se noVlan, ep1-ep2 same sw-port, vlan+vlan
        testData.with {
            def swT = owner.topologyHelper.switchTriplets.find { it.ep1 == it.ep2 }
            def haFlowBuilder = owner.haFlowFactory.getBuilder(swT).withSharedEndpointFullPort().withEp1AndEp2SameSwitchAndPort()
            add([swT: swT, haFlowBuilder: haFlowBuilder, coveredCases: ["noVlan, ep1-ep2 same sw-port, vlan+vlan"]])
        }
        //se qinq, ep1 default, ep2 qinq
        testData.with {
            def swT = owner.topologyHelper.switchTriplets.find { it.ep1 != it.ep2 }
            def haFlowBuilder = owner.haFlowFactory.getBuilder(swT).withSharedEndpointQnQ().withEp1FullPort().withEp2QnQ()
            add([swT: swT, haFlowBuilder: haFlowBuilder, coveredCases: ["se qinq, ep1 default, ep2 qinq"]])
        }
        //se qinq, ep1-ep2 same sw-port, qinq
        testData.with {
            def swT = owner.topologyHelper.switchTriplets.find { it.ep1 == it.ep2 }
            def haFlowBuilder = owner.haFlowFactory.getBuilder(swT).withSharedEndpointQnQ()
                    .withEp1AndEp2SameSwitchAndPort().withEp1AndEp2SameQnQ()
            add([swT: swT, haFlowBuilder: haFlowBuilder, coveredCases: ["se qinq, ep1-ep2 same sw-port, qinq"]])
        }
        return testData
    }

    def getSwTripletsTestData() {
        def requiredCases = [
                //se = shared endpoint, ep = subflow endpoint, yp = y-point
                [name     : "se is wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared
                 }],
                [name     : "se is non-wb and se!=yp",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared
                 }],
                [name     : "ep on wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "ep on non-wb and different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> !swT.ep1.wb5164 && swT.ep1 != swT.ep2 }],
                [name     : "se+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared
                 }],
                [name     : "se+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] == swT.shared
                 }],
                [name     : "yp on wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared && yPoints[0] != swT.ep1 && yPoints[0] != swT.ep2
                 }],
                [name     : "yp on non-wb and yp!=se!=ep",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && yPoints[0] != swT.shared && yPoints[0] != swT.ep1 && yPoints[0] != swT.ep2
                 }],
                [name     : "ep+yp on wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1 || yPoints[0] == swT.ep2)
                 }],
                [name     : "ep+yp on non-wb",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
                     !swT.shared.wb5164 && yPoints.size() == 1 && (yPoints[0] == swT.ep1 || yPoints[0] == swT.ep2)
                 }],
                [name     : "yp==se",
                 condition: { SwitchTriplet swT ->
                     def yPoints = topologyHelper.findPotentialYPoints(swT)
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
