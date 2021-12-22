package org.openkilda.functionaltests.spec.flows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_ACTION
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/pce-affinity-flows/")
class FlowAffinitySpec extends HealthCheckSpecification {

    @Tidy
    def "Can create more than 2 affinity flows"() {
        when: "Create flow1"
        def swPair = topologyHelper.getSwitchPairs()[0]
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
            assert northbound.getFlowHistory(it.flowId).find { it.action == CREATE_ACTION }.dumps
                    .find { it.type == "stateAfter" }?.affinityGroupId == flow1.flowId
        }

        when: "Delete flows"
        [flow1, flow2, flow3].each { it && flowHelperV2.deleteFlow(it.flowId) }
        def flowsAreDeleted = true

        then: "Flow1 history contains 'affinityGroupId' information in 'delete' operation"
        verifyAll(northbound.getFlowHistory(flow1.flowId).find { it.action == DELETE_ACTION }.dumps) {
            it.find { it.type == "stateBefore" }?.affinityGroupId
            !it.find { it.type == "stateAfter" }?.affinityGroupId
        }

        and: "Flow2 and flow3 histories does not have 'affinityGroupId' in 'delete' because it's gone after deletion of 'main' flow1"
        [ flow2, flow3].each {
            verifyAll(northbound.getFlowHistory(it.flowId).find { it.action == DELETE_ACTION }.dumps) {
                !it.find { it.type == "stateBefore" }?.affinityGroupId
                !it.find { it.type == "stateAfter" }?.affinityGroupId
            }
        }

        cleanup:
        !flowsAreDeleted && [flow1, flow2, flow3].each { it && flowHelperV2.deleteFlow(it.flowId) }
    }

    @Tidy
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
        topologyHelper.getSwitchPairs().find { swP1Candidate ->
            swPair1 = swP1Candidate
            swPair2 = (topologyHelper.getSwitchPairs(true) - swP1Candidate).find { swP2Candidate ->
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
        [flow, affinityFlow].each { it && flowHelperV2.deleteFlow(it.flowId) }
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

    }

    @Tidy
    def "Affinity flows can have no overlapping switches at all"() {
        given: "Two switch pairs with not overlapping short paths available"
        SwitchPair swPair1, swPair2
        topologyHelper.getAllNeighboringSwitchPairs().find { swP1Candidate ->
            swPair1 = swP1Candidate
            swPair2 = topologyHelper.getAllNeighboringSwitchPairs().find { swP2Candidate ->
                [swP2Candidate.src.dpId, swP2Candidate.dst.dpId].every {
                    it !in [swP1Candidate.src.dpId, swP1Candidate.dst.dpId]
                }
            }
        } ?: assumeTrue(false, "No suiting switches found")

        and: "First flow"
        def flow = flowHelperV2.randomFlow(swPair1)
        flowHelperV2.addFlow(flow)

        when: "Create affinity flow"
        def affinityFlow = flowHelperV2.randomFlow(swPair2, false, [flow]).tap { affinityFlowId = flow.flowId }
        flowHelperV2.addFlow(affinityFlow)

        then: "It's path has no overlapping segments with the first flow"
        pathHelper.convert(northbound.getFlowPath(flow.flowId))
                .intersect(pathHelper.convert(northbound.getFlowPath(affinityFlow.flowId))).empty

        cleanup:
        [flow, affinityFlow].each { it && flowHelperV2.deleteFlow(it.flowId) }
    }

    @Tidy
    def "Affinity flow on the same endpoints #willOrNot take the same path if main path cost #exceedsOrNot affinity penalty"() {
        given: "A neighboring switch pair with parallel ISLs"
        def swPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.paths.findAll { it.size() == 2 }.size() > 1
        } ?: assumeTrue(false, "Need a pair of parallel ISLs for this test")

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
        [flow, affinityFlow].each { it && flowHelperV2.deleteFlow(it.flowId) }
        linkProps && northbound.deleteLinkProps(linkProps)

        where:
        mainIslCost << [affinityIslCost + DEFAULT_COST, affinityIslCost + DEFAULT_COST + 1]
        expectSamePaths << [true, false]
        willOrNot = expectSamePaths ? "will" : "will not"
        exceedsOrNot = expectSamePaths ? "does not exceed" : "exceeds"
    }

    @Tidy
    def "Cannot create affinity flow if target flow has affinity with diverse flow"() {
        given: "Existing flows with diversity"
        def swPair = topologyHelper.getSwitchPairs()[0]
        def flow1 = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow1)
        def affinityFlow = flowHelperV2.randomFlow(swPair, false, [flow1]).tap { affinityFlowId = flow1.flowId }
        flowHelperV2.addFlow(affinityFlow)

        when: "Create affinity flow on the same switch pair"
        def affinityFlow2 = flowHelperV2.randomFlow(swPair, false, [flow1, affinityFlow]).tap { affinityFlowId = flow1.flowId; diverseFlowId = affinityFlow.flowId }
        northboundV2.addFlow(affinityFlow2)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        with(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not create flow"
            errorDescription == "Couldn't create diverse group with flow in the same affinity group"
        }

        when: "Create affinity flow on the same switch pair"
        def affinityFlow3 = flowHelperV2.randomFlow(swPair, false, [flow1, affinityFlow]).tap { affinityFlowId = affinityFlow.flowId; diverseFlowId = flow1.flowId }
        northboundV2.addFlow(affinityFlow3)

        then: "Error is returned"
        def e2 = thrown(HttpClientErrorException)
        e2.statusCode == HttpStatus.BAD_REQUEST
        with(e2.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not create flow"
            errorDescription == "Couldn't create diverse group with flow in the same affinity group"
        }

        cleanup:
        [flow1, affinityFlow].each { flowHelperV2.deleteFlow(it.flowId) }
        !e && flowHelperV2.deleteFlow(affinityFlow2.flowId)
        !e2 && affinityFlow3 && flowHelperV2.deleteFlow(affinityFlow3.flowId)
    }

    @Tidy
    def "Cannot create affinity flow if target flow has another diverse group"() {
        given: "Existing flows with diversity"
        def swPair = topologyHelper.getSwitchPairs()[0]
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
        northboundV2.addFlow(affinityFlow2)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        with(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not create flow"
            errorDescription == "Couldn't create a diverse group with flow in a different diverse group than main affinity flow"
        }
        cleanup:
        [flow1, flow2, affinityFlow, diverseFlow].each { flowHelperV2.deleteFlow(it.flowId) }
        !e && flowHelperV2.deleteFlow(affinityFlow2.flowId)
    }

    @Tidy
    def "Unable to create an affinity flow with a 1-switch flow"() {
        given: "A one-switch flow"
        def sw = topology.activeSwitches[0]
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(flow)

        when: "Try to create an affinity flow targeting the one-switch flow"
        def swPair = topologyHelper.getSwitchPairs(true).find { it.src.dpId == sw.dpId }
        def affinityFlow = flowHelperV2.randomFlow(swPair, false, [flow]).tap { affinityFlowId = flow.flowId }
        northboundV2.addFlow(affinityFlow)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        with(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not create flow"
            errorDescription == "Couldn't create affinity group with one-switch flow"
        }

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        !e && flowHelperV2.deleteFlow(affinityFlow.flowId)
    }

    @Tidy
    def "Error is returned if affinity_with references a non existing flow"() {
        when: "Create an affinity flow that targets non-existing flow"
        def swPair = topologyHelper.getSwitchPairs()[0]
        def flow = flowHelperV2.randomFlow(swPair).tap { diverseFlowId = NON_EXISTENT_FLOW_ID }
        northboundV2.addFlow(flow)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        with(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not create flow"
            errorDescription == "Failed to find diverse flow id $NON_EXISTENT_FLOW_ID"
        }

        cleanup:
        !e && flowHelperV2.deleteFlow(flow.flowId)
    }
}
