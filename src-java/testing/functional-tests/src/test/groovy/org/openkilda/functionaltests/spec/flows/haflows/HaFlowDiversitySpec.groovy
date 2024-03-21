package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowState

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify the ability to create diverse HA-Flows in the system.")
@Tags([HA_FLOW])
class HaFlowDiversitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    def "Able to create diverse Ha-Flows"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.findAll {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    } &&
                    topology.getRelatedIsls(it.shared).size() >= 5
        }.shuffled().first()
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three Ha-Flows with diversity enabled"
        def haFlow1 = HaFlowExtended.build(swT, northboundV2, topology).create()
        def haFlow2 = HaFlowExtended.build(swT, northboundV2, topology, false, haFlow1.occupiedEndpoints())
                .withDiverseFlow(haFlow1.haFlowId).create()
        def haFlow3 = HaFlowExtended.build(swT, northboundV2, topology, false,
                haFlow1.occupiedEndpoints() + haFlow1.occupiedEndpoints()).withDiverseFlow(haFlow2.haFlowId).create()

        then: "HA-Flow create response contains info about diverse haFlow"
        !haFlow1.diverseWithHaFlows
        haFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        haFlow3.diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()

        and: "All Ha-Flows have diverse HA-Flow IDs in response"
        haFlow1.retrieveDetails().diverseWithHaFlows.sort() == [haFlow2.haFlowId, haFlow3.haFlowId].sort()
        haFlow2.retrieveDetails().diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow3.haFlowId].sort()
        haFlow3.retrieveDetails().diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()

        and: "All Ha-Flows have different paths"
        def haFlow1InvolvedIsls, haFlow2InvolvedIsls, haFlow3InvolvedIsls
        withPool {
            (haFlow1InvolvedIsls, haFlow2InvolvedIsls, haFlow3InvolvedIsls) = [haFlow1, haFlow2, haFlow3].collectParallel {
                it.retrievedAllEntityPaths().getInvolvedIsls()
            }
        }

        haFlow1InvolvedIsls.intersect(haFlow2InvolvedIsls).isEmpty()
        haFlow2InvolvedIsls.intersect(haFlow3InvolvedIsls).isEmpty()
        haFlow1InvolvedIsls.intersect(haFlow3InvolvedIsls).isEmpty()

        and: "HA-Flow passes flow validation"
        withPool {
            [haFlow1, haFlow2, haFlow3].eachParallel { HaFlowExtended haFlow ->
                def validationResponse = haFlow.validate()
                assert validationResponse.asExpected
                assert validationResponse.getSubFlowValidationResults().every { it.getDiscrepancies().isEmpty() }
            }
        }

        cleanup:
        [haFlow1, haFlow2, haFlow3].findAll().each { haFlow -> haFlow.delete() }
    }

    def "Able to create HA-Flow diverse with regular flow that is already in diverse group with another HA-Flow"() {
        given: "Switches with two not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    it.pathsEp1.collect { pathHelper.getInvolvedIsls(it) }
                            .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create an HA-Flow without diversity"
        def haFlow1 = HaFlowExtended.build(swT, northboundV2, topology).create()

        and: "Create a regular multiSwitch Flow diverse with previously created HA-Flow"
        def flowRequest = flowHelperV2.randomFlow(swT.shared, swT.ep1, false)
                .tap { diverseFlowId = haFlow1.getHaFlowId() }
        def flow = flowHelperV2.addFlow(flowRequest)

        and: "Create an additional HA-Flow diverse with simple flow that has another HA-Flow in diverse group"
        def haFlow2 = HaFlowExtended.build(swT, northboundV2, topology, false, haFlow1.occupiedEndpoints())
                .withDiverseFlow(flow.flowId).create()

        then: "Create response contains correct info about diverse flows"
        !haFlow1.diverseWithHaFlows
        !haFlow1.diverseWithFlows
        !haFlow1.diverseWithYFlows
        flow.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !flow.diverseWith
        !flow.diverseWithYFlows
        haFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        haFlow2.diverseWithFlows == [flow.flowId] as Set
        !haFlow2.diverseWithYFlows

        when: "Get Flow and Ha-Flows details"
        def haFlow1Details = haFlow1.retrieveDetails()
        def haFlow2Details = haFlow2.retrieveDetails()
        def regularFlowDetails = northboundV2.getFlow(flow.flowId)

        then: "All get Flow responses have correct diverse flow IDs"
        haFlow1Details.diverseWithHaFlows == [haFlow2.haFlowId] as Set
        haFlow1Details.diverseWithFlows == [flow.flowId] as Set
        !haFlow1Details.diverseWithYFlows
        haFlow2Details.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        haFlow2Details.diverseWithFlows == [flow.flowId] as Set
        !haFlow2Details.diverseWithYFlows
        regularFlowDetails.diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()
        !regularFlowDetails.diverseWith
        !regularFlowDetails.diverseWithYFlows

        and: "HA-Flow passes flow validation"
        withPool {
            [haFlow1, haFlow2].eachParallel { HaFlowExtended haFlow ->
                def validationResponse = haFlow.validate()
                assert validationResponse.asExpected
                assert validationResponse.getSubFlowValidationResults().every { it.getDiscrepancies().isEmpty()}
            }
        }

        cleanup:
        [haFlow1, haFlow2].each { haFlow -> haFlow && haFlow.delete() }
    }

    def "Able to create HA-Flow diverse with Y-Flow that is in diverse group with another HA-Flow"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create an HA-Flow without diversity"
        def haFlow1 = HaFlowExtended.build(swT, northboundV2, topology).create()

        and: "Create a Y-Flow diverse with previously created HA-Flow"
        def yFlow = yFlowFactory.getBuilder(swT, false).withDiverseFlow(haFlow1.haFlowId).build()
        yFlow = yFlow.waitForBeingInState(FlowState.UP)

        and: "Create an additional HA-Flow diverse with Y-Flow that has another HA-Flow in diverse group"
        def haFlow2 = HaFlowExtended.build(swT, northboundV2, topology, false, haFlow1.occupiedEndpoints())
                .withDiverseFlow(yFlow.yFlowId).create()

        then: "The last HA-Flow create response contains info about diverse haFlow"
        !haFlow1.diverseWithHaFlows
        !haFlow1.diverseWithFlows
        !haFlow1.diverseWithYFlows
        yFlow.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !yFlow.diverseWithFlows
        !yFlow.diverseWithYFlows
        haFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !haFlow2.diverseWithFlows
        haFlow2.diverseWithYFlows == [yFlow.yFlowId] as Set

        when: "Get Y-flow and Ha-Flows details"
        def haFlow1Details = haFlow1.retrieveDetails()
        def haFlow2Details = haFlow2.retrieveDetails()
        def yFlowDetails = northboundV2.getYFlow(yFlow.yFlowId)

        then: "All get flow responses have correct diverse flow IDs"
        haFlow1Details.diverseWithHaFlows == [haFlow2.haFlowId] as Set
        !haFlow1Details.diverseWithFlows
        haFlow1Details.diverseWithYFlows == [yFlow.yFlowId] as Set
        haFlow2Details.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !haFlow2Details.diverseWithFlows
        haFlow2Details.diverseWithYFlows == [yFlow.yFlowId] as Set
        yFlowDetails.diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()
        !yFlowDetails.diverseWithFlows
        !yFlowDetails.diverseWithYFlows

        and: "HA-Flow passes flow validation"
        withPool {
            [haFlow1, haFlow2].eachParallel { HaFlowExtended haFlow ->
                def validationResponse = haFlow.validate()
                assert validationResponse.asExpected
                assert validationResponse.getSubFlowValidationResults().every { it.getDiscrepancies().isEmpty()}
            }
        }

        cleanup:
        [haFlow1, haFlow2].each { haFlow -> haFlow && haFlow.delete() }
        yFlow && yFlow.delete()
    }
}
