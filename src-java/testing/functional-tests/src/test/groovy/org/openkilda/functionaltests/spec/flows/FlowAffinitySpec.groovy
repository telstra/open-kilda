package org.openkilda.functionaltests.spec.flows

import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_ACTION
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static groovyx.gpars.GParsPool.withPool

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.info.event.PathNode
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/pce-affinity-flows/")
class FlowAffinitySpec extends HealthCheckSpecification {

    def "Can create more than 2 affinity flows"() {
        when: "Create flow1"
        def swPair = switchPairs.all().random()
        def flow1 = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow1)

        and: "Create flow2 with affinity to flow1"
        def flow2 = flowHelperV2.randomFlow(swPair, false, [flow1]).tap { affinityFlowId = flow1.flowId }
        flowHelperV2.addFlow(flow2)

        and: "Create flow3 with affinity to flow2"
        def flow3 = flowHelperV2.randomFlow(swPair, false, [flow1, flow2]).tap { affinityFlowId = flow2.flowId }
        flowHelperV2.addFlow(flow3)

        then: "All flows have affinity with flow1"
        //yes, even flow1 with flow1, by design
        [flow1, flow2, flow3].each {
            assert northboundV2.getFlow(it.flowId).affinityWith == flow1.flowId
        }

        and: "Flows' histories contain 'affinityGroupId' information"
        [flow2, flow3].each {//flow1 had no affinity at the time of creation
            assert flowHelper.getEarliestHistoryEntryByAction(it.flowId, CREATE_ACTION).dumps
                    .find { it.type == "stateAfter" }?.affinityGroupId == flow1.flowId
        }

        when: "Delete flows"
        [flow1, flow2, flow3].each { it && flowHelperV2.deleteFlow(it.flowId) }

        then: "Flow1 history contains 'affinityGroupId' information in 'delete' operation"
        verifyAll(flowHelper.getEarliestHistoryEntryByAction(flow1.flowId, DELETE_ACTION).dumps) {
            it.find { it.type == "stateBefore" }?.affinityGroupId
            !it.find { it.type == "stateAfter" }?.affinityGroupId
        }

        and: "Flow2 and flow3 histories does not have 'affinityGroupId' in 'delete' because it's gone after deletion of 'main' flow1"
        [ flow2, flow3].each {
            verifyAll(flowHelper.getEarliestHistoryEntryByAction(it.flowId, DELETE_ACTION).dumps) {
                !it.find { it.type == "stateBefore" }?.affinityGroupId
                !it.find { it.type == "stateAfter" }?.affinityGroupId
            }
        }
    }

    def "Affinity flows are created close even if cost is not optimal, same dst"() {
        given: "Two switch pairs with same dst and diverse paths available"
        SwitchPair swPair1, swPair2
        List<PathNode> path1, uncommonPath2
        List<List<PathNode>> leastUncommonPaths2
        /* path1 is a path on swPair1 and is for the main flow. uncommonPath2 is a path on swPair2 and has many
        'uncommon' ISLs with path1, this is for the affinity flow. This path will be forced to be the most optimal
        cost-wise. leastUncommonPaths2 are paths on swPair2 that have the least uncommon ISLs with path1 (but not
        guaranteed to have any common ones); one of these paths should be chosen regardless of cost-optimal
        uncommonPath2 when creating affinity flow */
        switchPairs.all().getSwitchPairs().find{ swP1Candidate ->
            swPair1 = swP1Candidate
            swPair2 = (switchPairs.all().getSwitchPairs() - swP1Candidate).find { swP2Candidate ->
                if (swP1Candidate.dst.dpId != swP2Candidate.dst.dpId) return false
                path1 = swP1Candidate.paths.find { path1Candidate ->
                    List<Tuple2<List<PathNode>, Integer>> scoreList = []
                    swP2Candidate.paths.each {
                        def uncommon = it - it.intersect(path1Candidate)
                        scoreList << new Tuple2(it, uncommon.size())
                    }
                    if (scoreList.size() < 2) return false
                    scoreList.sort { it.v2 }
                    if (scoreList[0].v2 == scoreList[-1].v2) return false
                    uncommonPath2 = scoreList[-1].v1
                    leastUncommonPaths2 = scoreList.findAll { it.v2 == scoreList[0].v2 }*.v1
                }
            }
        } ?: assumeTrue(false, "No suiting switches/paths found")

        and: "Existing flow over one of the switch pairs"
        def flow = flowHelperV2.randomFlow(swPair1)
        swPair1.paths.findAll { it != path1 }.each { pathHelper.makePathMorePreferable(path1, it) }
        flowHelperV2.addFlow(flow)
        assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == path1
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

        and: "Potential affinity flow, which optimal path is diverse from the main flow, but it has a not optimal closer path"
        def affinityFlow = flowHelperV2.randomFlow(swPair2, false, [flow]).tap { affinityFlowId = flow.flowId }
        swPair2.paths.findAll { it != uncommonPath2 }.each { pathHelper.makePathMorePreferable(uncommonPath2, it) }

        when: "Build affinity flow"
        flowHelperV2.addFlow(affinityFlow)

        then: "Most optimal, but 'uncommon' to the main flow path is NOT picked, but path with least uncommon ISLs is chosen"
        def actualAffinityPath = pathHelper.convert(northbound.getFlowPath(affinityFlow.flowId))
        leastUncommonPaths2.any { actualAffinityPath == it }

        and: "Path remains the same when manual reroute is called"
        !northboundV2.rerouteFlow(affinityFlow.flowId).rerouted

        cleanup:
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

    }

    def "Affinity flows can have no overlapping switches at all"() {
        given: "Two switch pairs with not overlapping short paths available"
        def swPair1 = switchPairs.all().neighbouring().random()
        def swPair2 = switchPairs.all().neighbouring().excludeSwitches([swPair1.src, swPair1.dst]).random()

        and: "First flow"
        def flow = flowHelperV2.randomFlow(swPair1)
        flowHelperV2.addFlow(flow)

        when: "Create affinity flow"
        def affinityFlow = flowHelperV2.randomFlow(swPair2, false, [flow]).tap { affinityFlowId = flow.flowId }
        flowHelperV2.addFlow(affinityFlow)

        then: "It's path has no overlapping segments with the first flow"
        pathHelper.convert(northbound.getFlowPath(flow.flowId))
                .intersect(pathHelper.convert(northbound.getFlowPath(affinityFlow.flowId))).empty
    }

    def "Affinity flow on the same endpoints #willOrNot take the same path if main path cost #exceedsOrNot affinity penalty"() {
        given: "A neighboring switch pair with parallel ISLs"
        def swPair = switchPairs.all().neighbouring().withAtLeastNIslsBetweenNeighbouringSwitches(2).random()

        and: "First flow"
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)

        and: "Isl which is taken by the main flow weighs more/less than the neighboring ISL, taking into account affinity penalty"
        def isls = pathHelper.getInvolvedIsls(flow.flowId)
        assert isls.size() == 1
        def linkProps = [islUtils.toLinkProps(isls[0], ["cost": mainIslCost.toString()])]
        northbound.updateLinkProps(linkProps)

        when: "Create affinity flow on the same switch pair"
        def affinityFlow = flowHelperV2.randomFlow(swPair, false, [flow]).tap { affinityFlowId = flow.flowId }
        flowHelperV2.addFlow(affinityFlow)

        then: "It takes/doesn't take the path of the main flow"
        (pathHelper.convert(northbound.getFlowPath(flow.flowId)) ==
                pathHelper.convert(northbound.getFlowPath(affinityFlow.flowId))) == expectSamePaths

        cleanup:
        linkProps && northbound.deleteLinkProps(linkProps)

        where:
        mainIslCost << [affinityIslCost + DEFAULT_COST, affinityIslCost + DEFAULT_COST + 1]
        expectSamePaths << [true, false]
        willOrNot = expectSamePaths ? "will" : "will not"
        exceedsOrNot = expectSamePaths ? "does not exceed" : "exceeds"
    }

    def "Cannot create affinity flow if target flow has affinity with diverse flow"() {
        given: "Existing flows with diversity"
        def swPair = switchPairs.all().random()
        def flow1 = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow1)
        def affinityFlow = flowHelperV2.randomFlow(swPair, false, [flow1]).tap { affinityFlowId = flow1.flowId }
        flowHelperV2.addFlow(affinityFlow)
        def expectedError =
                new FlowNotCreatedExpectedError(~/Couldn't create diverse group with flow in the same affinity group/)

        when: "Create affinity flow on the same switch pair"
        def affinityFlow2 = flowHelperV2.randomFlow(swPair, false, [flow1, affinityFlow]).tap { affinityFlowId = flow1.flowId; diverseFlowId = affinityFlow.flowId }
        flowHelperV2.addFlow(affinityFlow2)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        expectedError.matches(e)
        when: "Create affinity flow on the same switch pair"
        def affinityFlow3 = flowHelperV2.randomFlow(swPair, false, [flow1, affinityFlow]).tap { affinityFlowId = affinityFlow.flowId; diverseFlowId = flow1.flowId }
        flowHelperV2.addFlow(affinityFlow3)

        then: "Error is returned"
        def e2 = thrown(HttpClientErrorException)
        expectedError.matches(e2)
    }

    def "Cannot create affinity flow if target flow has another diverse group"() {
        given: "Existing flows with diversity"
        def swPair = switchPairs.all().random()
        def flow1 = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow1)
        def affinityFlow = flowHelperV2.randomFlow(swPair, false, [flow1]).tap { affinityFlowId = flow1.flowId }
        flowHelperV2.addFlow(affinityFlow)
        def diverseFlow = flowHelperV2.randomFlow(swPair, false, [flow1]).tap { diverseFlowId = flow1.flowId }
        flowHelperV2.addFlow(diverseFlow)
        def flow2 = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow2)

        when: "Create an affinity flow that targets the diversity flow"
        def affinityFlow2 = flowHelperV2.randomFlow(swPair, false, [flow1, diverseFlow]).tap { affinityFlowId = flow1.flowId; diverseFlowId = flow2.flowId }
        flowHelperV2.addFlow(affinityFlow2)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/Couldn't create a diverse group with flow in a different diverse group than main affinity flow/).matches(e)
    }

    def "Able to create an affinity flow with a 1-switch flow"() {
        given: "A one-switch flow"
        def sw = topology.activeSwitches[0]
        def oneSwitchFlow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(oneSwitchFlow)

        when: "Create an affinity flow targeting the one-switch flow"
        def swPair = switchPairs.all().includeSourceSwitch(sw).random()
        def affinityFlow = flowHelperV2.randomFlow(swPair, false, [oneSwitchFlow]).tap { affinityFlowId = oneSwitchFlow.flowId }
        flowHelperV2.addFlow(affinityFlow)

        then: "Both flows have affinity with flow1"
        //yes, even flow1 with flow1, by design
        withPool {
            [oneSwitchFlow, affinityFlow].eachParallel {
                assert northboundV2.getFlow(it.flowId).affinityWith == oneSwitchFlow.flowId
            }
        }

        and: "Affinity flow history contain 'affinityGroupId' information"
            assert flowHelper.getEarliestHistoryEntryByAction(affinityFlow.flowId, CREATE_ACTION).dumps
                    .find { it.type == "stateAfter" }?.affinityGroupId == oneSwitchFlow.flowId
    }

    def "Error is returned if affinity_with references a non existing flow"() {
        when: "Create an affinity flow that targets non-existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowHelperV2.randomFlow(swPair).tap { diverseFlowId = NON_EXISTENT_FLOW_ID }
        flowHelperV2.addFlow(flow)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Failed to find diverse flow id $NON_EXISTENT_FLOW_ID/).matches(e)
    }
}
