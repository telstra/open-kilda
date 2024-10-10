package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.SwitchPair

import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/pce-affinity-flows/")

class FlowAffinitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    FlowFactory flowFactory

    def "Can create more than 2 affinity flows"() {
        when: "Create flow1"
        def swPair = switchPairs.all().random()
        def flow1 = flowFactory.getRandom(swPair)

        and: "Create flow2 with affinity to flow1"
        def flow2 = flowFactory.getBuilder(swPair, false, flow1.occupiedEndpoints())
                .withAffinityFlow(flow1.flowId ).build()
                .create()

        and: "Create flow3 with affinity to flow2"
        def flow3 = flowFactory.getBuilder(swPair, false, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))
                .withAffinityFlow(flow2.flowId).build()
                .create()

        then: "All flows have affinity with flow1"
        //yes, even flow1 with flow1, by design
        [flow1, flow2, flow3].each {flow ->
            assert flow.retrieveDetails().affinityWith == flow1.flowId
        }

        and: "Flows' histories contain 'affinityGroupId' information"
        [flow2, flow3].each {flow -> //flow1 had no affinity at the time of creation
            assert flow.retrieveFlowHistory().getEntriesByType(FlowActionType.CREATE).first().dumps
                    .find { it.type == "stateAfter" }?.affinityGroupId == flow1.flowId
        }

        when: "Delete flows"
        [flow1, flow2, flow3].each { flow ->  flow.delete() }

        then: "Flow1 history contains 'affinityGroupId' information in 'delete' operation"
        verifyAll(flow1.retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).first().dumps) {
            it.find { it.type == "stateBefore" }?.affinityGroupId
            !it.find { it.type == "stateAfter" }?.affinityGroupId
        }

        and: "Flow2 and flow3 histories does not have 'affinityGroupId' in 'delete' because it's gone after deletion of 'main' flow1"
        [ flow2, flow3].each {flow ->
            verifyAll(flow.retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).first().dumps) {
                !it.find { it.type == "stateBefore" }?.affinityGroupId
                !it.find { it.type == "stateAfter" }?.affinityGroupId
            }
        }
    }

    def "Affinity flows are created close even if cost is not optimal, same dst"() {
        given: "Two switch pairs with same dst and diverse paths available"
        SwitchPair swPair1, swPair2
        List<Isl> flowExpectedPath, uncommonFlowPathIsls
        List<Path> leastUncommonPaths2
        /* flowExpectedPath is a path on swPair1 and is for the main flow. uncommonFlowPathIsls is a path on swPair2 and has many
        'uncommon' ISLs with flowExpectedPath, this is for the affinity flow. This path will be forced to be the most optimal
        cost-wise. leastUncommonPaths2 are paths on swPair2 that have the least uncommon ISLs with flowExpectedPath (but not
        guaranteed to have any common ones); one of these paths should be chosen regardless of cost-optimal
        uncommonFlowPathIsls when creating affinity flow */
        switchPairs.all().getSwitchPairs().find{ swP1Candidate ->
            swPair1 = swP1Candidate
            swPair2 = switchPairs.all().excludePairs([swP1Candidate]).getSwitchPairs().find { swP2Candidate ->
                if (swP1Candidate.dst.dpId != swP2Candidate.dst.dpId) return false
                flowExpectedPath = swP1Candidate.retrieveAvailablePaths().find { path1Candidate ->
                    List<Tuple2<Path, Integer>> scoreList = []
                    swP2Candidate.retrieveAvailablePaths().each {
                        def pathNodes = it.retrieveNodes()
                        def uncommon = pathNodes - pathNodes.intersect(path1Candidate.retrieveNodes())
                        scoreList << new Tuple2(it, uncommon.size())
                    }
                    if (scoreList.size() < 2) return false
                    scoreList.sort { it.v2 }
                    if (scoreList[0].v2 == scoreList[-1].v2) return false
                    uncommonFlowPathIsls = scoreList[-1].v1.getInvolvedIsls() as List<Isl>
                    leastUncommonPaths2 = scoreList.findAll { it.v2 == scoreList[0].v2 }*.v1
                }.getInvolvedIsls() as List<Isl>
            }
        } ?: assumeTrue(false, "No suiting switches/paths found")

        and: "Existing flow over one of the switch pairs"
        swPair1.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { !it.containsAll(flowExpectedPath) }
                .each { islHelper.makePathIslsMorePreferable(flowExpectedPath, it) }
        def flow = flowFactory.getRandom(swPair1)
        def initialFlowPath = flow.retrieveAllEntityPaths()
        assert initialFlowPath.flowPath.getInvolvedIsls() == flowExpectedPath
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

        and: "Potential affinity flow, which optimal path is diverse from the main flow, but it has a not optimal closer path"
        def affinityFlow = flowFactory.getBuilder(swPair2, false, flow.occupiedEndpoints())
                .withAffinityFlow(flow.flowId).build()
        swPair2.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { it != uncommonFlowPathIsls }
                .each { islHelper.makePathIslsMorePreferable(uncommonFlowPathIsls, it) }

        when: "Build affinity flow"
        affinityFlow.create()

        then: "Most optimal, but 'uncommon' to the main flow path is NOT picked, but path with least uncommon ISLs is chosen"
        def actualAffinityPathIsls = affinityFlow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        assert actualAffinityPathIsls != uncommonFlowPathIsls
        leastUncommonPaths2.find { it.getInvolvedIsls() == actualAffinityPathIsls}

        and: "Path remains the same when manual reroute is called"
        !affinityFlow.reroute().rerouted
    }

    def "Affinity flows can have no overlapping switches at all"() {
        given: "Two switch pairs with not overlapping short paths available"
        def swPair1 = switchPairs.all().neighbouring().random()
        def swPair2 = switchPairs.all().neighbouring().excludeSwitches([swPair1.src, swPair1.dst]).random()

        and: "First flow"
        def flow = flowFactory.getRandom(swPair1)

        when: "Create affinity flow"
        def affinityFlow = flowFactory.getBuilder(swPair2, false, flow.occupiedEndpoints())
                .withAffinityFlow(flow.flowId).build()
                .create()

        then: "Affinity flow path has no overlapping ISLs with the first flow"
        assert affinityFlow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
                .intersect(flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()).isEmpty()
    }

    def "Affinity flow on the same endpoints #willOrNot take the same path if main path cost #exceedsOrNot affinity penalty"() {
        given: "A neighboring switch pair with parallel ISLs"
        def swPair = switchPairs.all().neighbouring().withAtLeastNIslsBetweenNeighbouringSwitches(2).random()

        and: "First flow"
        def flow = flowFactory.getRandom(swPair)

        and: "Isl which is taken by the main flow weighs more/less than the neighboring ISL, taking into account affinity penalty"
        def flowIsls = flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        assert flowIsls.size() == 1
        islHelper.updateIslsCost([flowIsls[0]], mainIslCost)

        when: "Create affinity flow on the same switch pair"
        def affinityFlow = flowFactory.getBuilder(swPair, false, flow.occupiedEndpoints())
                .withAffinityFlow(flow.flowId).build()
                .create()
        def affinityFlowIsls = affinityFlow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()

        then: "It takes/doesn't take the path of the main flow"
        (flowIsls.sort() == affinityFlowIsls.sort()) == expectSamePaths

        where:
        mainIslCost << [affinityIslCost + DEFAULT_COST, affinityIslCost + DEFAULT_COST + 1]
        expectSamePaths << [true, false]
        willOrNot = expectSamePaths ? "will" : "will not"
        exceedsOrNot = expectSamePaths ? "does not exceed" : "exceeds"
    }

    def "Cannot create affinity flow if target flow has affinity with diverse flow"() {
        given: "Existing flows with diversity"
        def swPair = switchPairs.all().random()
        def flow1 = flowFactory.getRandom(swPair)

        def busyEndpoints = flow1.occupiedEndpoints()
        def affinityFlow = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withAffinityFlow(flow1.flowId).build()
                .create()
        busyEndpoints.addAll(affinityFlow.occupiedEndpoints())

        when: "Create affinity flow on the same switch pair"
        def affinityFlow2 = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withAffinityFlow(flow1.flowId)
                .withDiverseFlow(affinityFlow.flowId).build()
        affinityFlow2.create()

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Couldn't create diverse group with flow in the same affinity group/).matches(e)

        when: "Create affinity flow on the same switch pair"
        def affinityFlow3 = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withAffinityFlow(affinityFlow.flowId)
                .withDiverseFlow(flow1.flowId).build()
        affinityFlow3.create()

        then: "Error is returned"
        def e2 = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Couldn't create diverse group with flow in the same affinity group/).matches(e2)
    }

    def "Cannot create affinity flow if target flow has another diverse group"() {
        given: "Existing flows with diversity"
        def swPair = switchPairs.all().random()
        def flow1 = flowFactory.getRandom(swPair)
        def busyEndpoints = flow1.occupiedEndpoints()
        def affinityFlow = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withAffinityFlow(flow1.flowId).build()
                .create()
        busyEndpoints.addAll(affinityFlow.occupiedEndpoints())

        def diverseFlow = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withDiverseFlow(flow1.flowId).build()
                .create()
        busyEndpoints.addAll(diverseFlow.occupiedEndpoints())

        def flow2 = flowFactory.getRandom(swPair, false, FlowState.UP, busyEndpoints)
        busyEndpoints.addAll(flow2.occupiedEndpoints())

        when: "Create an affinity flow that targets the diversity flow"
        def affinityFlow2 = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withAffinityFlow(flow1.flowId)
                .withDiverseFlow(flow2.flowId).build()
        affinityFlow2.create()

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/Couldn't create a diverse group with flow in a different diverse group than main affinity flow/).matches(e)
    }

    def "Able to create an affinity flow with a 1-switch flow"() {
        given: "A one-switch flow"
        def sw = topology.activeSwitches[0]
        def oneSwitchFlow = flowFactory.getRandom(sw, sw)

        when: "Create an affinity flow targeting the one-switch flow"
        def swPair = switchPairs.all().includeSourceSwitch(sw).random()
        def affinityFlow = flowFactory.getBuilder(swPair, false, oneSwitchFlow.occupiedEndpoints())
                .withAffinityFlow(oneSwitchFlow.flowId).build()
                .create()

        then: "Both flows have affinity with flow1"
        //yes, even flow1 with flow1, by design
        withPool {
            [oneSwitchFlow, affinityFlow].eachParallel { FlowExtended flow ->
                assert flow.retrieveDetails().affinityWith == oneSwitchFlow.flowId
            }
        }

        and: "Affinity flow history contain 'affinityGroupId' information"
            assert affinityFlow.retrieveFlowHistory().getEntriesByType(FlowActionType.CREATE).first().dumps
                    .find { it.type == "stateAfter" }?.affinityGroupId == oneSwitchFlow.flowId
    }

    def "Error is returned if affinity_with references a non existing flow"() {
        when: "Create an affinity flow that targets non-existing flow"
        def swPair = switchPairs.all().random()
        flowFactory.getBuilder(swPair).withAffinityFlow(NON_EXISTENT_FLOW_ID).build()
                .create()
        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Failed to find affinity flow id $NON_EXISTENT_FLOW_ID/).matches(e)
    }
}
