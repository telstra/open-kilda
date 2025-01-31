package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.SwitchHelper.getRandomAvailablePort
import static org.openkilda.messaging.payload.flow.FlowState.UP

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEntityPath
import org.openkilda.functionaltests.helpers.model.Path

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/pce-diverse-flows")
@Narrative("""
This test suite verifies the ability to create diverse flows in the system. Diverse flows are flows that should not 
overlap at all or the overlapping should be minimal. Such flows form a so-called diversity group. Ideally, the diversity 
group should not have flows with overlapping paths. But it depends on the cost of paths. The paths of flows from 
the same diversity group may overlap if the cost of each non-overlapping path is more than the cost of the overlapping 
path. The cost of paths for diverse flows is calculated in real time and consists of the following parameters: 

1. The cost of ISL involved in the flow path (taken from DB);
2. (diversity.switch.cost) * (the number of diverse flows going through this switch);
3. (diversity.isl.cost) * (the number of diverse flows going through this ISL). 

Refer to https://github.com/telstra/open-kilda/issues/1231 for more details.
""")

class FlowDiversitySpec extends HealthCheckSpecification {

    @Value('${diversity.isl.cost}')
    int diversityIslCost

    @Value('${diversity.switch.cost}')
    int diversitySwitchCost

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags(SMOKE)
    def "Able to create diverse flows"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(3).random()

        when: "Create three flows with diversity enabled"
        def flow1 = flowFactory.getBuilder(switchPair, false).build()
                .sendCreateRequest()

        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build()
                .sendCreateRequest()

        def flow3 = flowFactory.getBuilder(switchPair, false, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))
                .withDiverseFlow(flow2.flowId).build()
                .sendCreateRequest()

        then: "Flow create response contains information about diverse flow"
        !flow1.diverseWith
        flow2.diverseWith.sort() == [flow1.flowId]
        flow3.diverseWith.sort() == [flow1.flowId, flow2.flowId].sort()
        [flow1, flow2, flow3].each { flow -> flow.waitForBeingInState(UP) }

        and: "All flows have diverse flow IDs in response"
        flow1.retrieveDetails().diverseWith.sort() == [flow2.flowId, flow3.flowId].sort()
        flow2.retrieveDetails().diverseWith.sort() == [flow1.flowId, flow3.flowId].sort()
        flow3.retrieveDetails().diverseWith.sort() == [flow1.flowId, flow2.flowId].sort()

        and: "All flows have different paths"
        def allInvolvedIsls = [flow1, flow2, flow3].collectMany { flow ->
           flow.retrieveAllEntityPaths().getInvolvedIsls()
        }
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Flows' histories contain 'diverseGroupId' information"
        [flow2, flow3].each { flow ->//flow1 had no diversity at the time of creation
            assert flow.retrieveFlowHistory().getEntriesByType(FlowActionType.CREATE).first().dumps
                    .find { it.type == "stateAfter" }?.diverseGroupId
        }

        when: "Delete flows"
        [flow1, flow2, flow3].each {flow -> flow.delete() }

        then: "Flows' histories contain 'diverseGroupId' information in 'delete' operation"
        [flow1, flow2].each {flow ->
            verifyAll(flow.retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).first().dumps) {
                it.find { it.type == "stateBefore" }?.diverseGroupId
                !it.find { it.type == "stateAfter" }?.diverseGroupId
            }
        }
        //except flow3, because after deletion of flow1/flow2 flow3 is no longer in the diversity group
        verifyAll(flow3.retrieveFlowHistory().getEntriesByType(FlowActionType.DELETE).first().dumps) {
            !it.find { it.type == "stateBefore" }?.diverseGroupId
            !it.find { it.type == "stateAfter" }?.diverseGroupId
        }
    }

    def "Able to update flows to become diverse"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(3).random()

        and: "Create three flows"
        def flow1 = flowFactory.getRandom(switchPair, false)
        def flow2 = flowFactory.getRandom(switchPair, false, UP, flow1.occupiedEndpoints())
        def flow3 = flowFactory.getRandom(switchPair, false, UP, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))

        def (flow1Path, flow2Path, flow3Path) = [flow1, flow2, flow3].collect {flow ->
            flow.retrieveAllEntityPaths()
        }
        assert [flow1Path.getPathNodes(), flow2Path.getPathNodes(), flow3Path.getPathNodes()].toSet().size() == 1

        when: "Update the second flow to become diverse"
        def updatedFlow2 = flow2.update(flow2.deepCopy().tap { it.diverseWith = [flow1.flowId]})

        and: "Second flow's history contains 'groupId' information"
        def historyEvent = updatedFlow2.waitForHistoryEvent(FlowActionType.UPDATE)
        verifyAll {
            !historyEvent.dumps.find { it.type == "stateBefore" }?.diverseGroupId
            historyEvent.dumps.find { it.type == "stateAfter" }?.diverseGroupId
        }

        then: "Update response contains information about diverse flow"
        updatedFlow2.diverseWith.sort() == [flow1.flowId]

        and: "The flow became diverse and changed the path"
        def flow2PathUpdated = updatedFlow2.retrieveAllEntityPaths()
        flow2PathUpdated.getPathNodes() != flow2Path.getPathNodes()

        and: "All flows except last one have the 'diverse_with' field"
        flow1.retrieveDetails().diverseWith == [flow2.flowId].toSet()
        flow2.retrieveDetails().diverseWith == [flow1.flowId].toSet()
        !flow3.retrieveDetails().diverseWith

        when: "Update the third flow to become diverse"
        def updatedFlow3 = flow3.update(flow3.deepCopy().tap { it.diverseWith = [flow2.flowId]})
        updatedFlow3.waitForHistoryEvent(FlowActionType.UPDATE)

        then: "The flow became diverse and all flows have different paths"
        def flow3PathUpdated = updatedFlow3.retrieveAllEntityPaths()
        [flow1Path.getPathNodes(), flow2PathUpdated.getPathNodes(), flow3PathUpdated.getPathNodes()].toSet().size() == 3

        def allInvolvedIsls = [flow1Path, flow2PathUpdated, flow3PathUpdated].collectMany { path ->
            path.getInvolvedIsls()
        }
        allInvolvedIsls.unique(false) == allInvolvedIsls
    }

    @Tags(SMOKE)
    def "Able to update flows to become not diverse"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(3).random()

        and: "Create three flows with diversity enabled"
        def flow1 = flowFactory.getRandom(switchPair, false)
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build()
                .create()
        def flow3 = flowFactory.getBuilder(switchPair, false, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))
                .withDiverseFlow(flow2.flowId).build()
                .create()

        def (flow1Path, flow2Path, flow3Path) = [flow1, flow2, flow3].collect {flow ->
           flow.retrieveAllEntityPaths()
        }
        def allInvolvedIsls = [flow1Path, flow2Path, flow3Path].collectMany { path -> path.getInvolvedIsls()}
        assert allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Flow1 path is the most preferable"
        def flow1Isls = flow1Path.getInvolvedIsls()
        switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { !it.containsAll(flow1Isls) }
                .each { islHelper.makePathIslsMorePreferable(flow1Isls, it) }

        when: "Update the second flow to become not diverse"
        flow2.update(flow2.deepCopy().tap { it.diverseWith = [""]})
        flow2.waitForHistoryEvent(FlowActionType.UPDATE)

        then: "The flow became not diverse and rerouted to the more preferable path (path of the first flow)"
        def flow2PathUpdated = flow2.retrieveAllEntityPaths()
        flow2PathUpdated.getInvolvedIsls() != flow2Path.getInvolvedIsls()
        flow2PathUpdated.getInvolvedIsls() == flow1Path.getInvolvedIsls()

        and: "The 'diverse_with' field is removed"
        !flow2.retrieveDetails().diverseWith

        and: "The flow's history reflects the change of 'groupId' field"
        verifyAll(flow2.retrieveFlowHistory().getEntriesByType(FlowActionType.UPDATE).first().dumps) {
            //https://github.com/telstra/open-kilda/issues/3807
//            it.find { it.type == "stateBefore" }.groupId
            !it.find { it.type == "stateAfter" }.diverseGroupId
        }

        when: "Update the third flow to become not diverse"
        flow3.update(flow3.deepCopy().tap { it.diverseWith = [""]})
        flow3.waitForHistoryEvent(FlowActionType.UPDATE)

        then: "The flow became not diverse and rerouted to the more preferable path (path of the first flow)"
        def flow3PathUpdated = flow3.retrieveAllEntityPaths()
        flow3PathUpdated.getInvolvedIsls() != flow3Path.getInvolvedIsls()
        flow3PathUpdated.getInvolvedIsls() == flow1Path.getInvolvedIsls()

        and: "The 'diverse_with' field is removed"
        !flow3.retrieveDetails().diverseWith
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Diverse flows are built through the same path if there are no alternative paths available"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "Create a flow going through these switches"
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1Path = flow1.retrieveAllEntityPaths().getPathNodes()

        and: "Make all alternative paths unavailable (bring ports down on the source switch)"
        def broughtDownIsls = topology.getRelatedIsls(switchPair.getSrc())
                .findAll {it.srcPort != flow1Path.first().portNo}
        islHelper.breakIsls(broughtDownIsls)

        when: "Create the second flow with diversity enabled"
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build()
                .create()
        def flow2Path = flow2.retrieveAllEntityPaths().getPathNodes()

        then: "The second flow is built through the same path as the first flow"
        flow2Path == flow1Path
    }

    @Tags(SMOKE)
    def "Links and switches get extra cost that is considered while calculating diverse flow paths"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all()
                .neighbouring()
                .withAtLeastNNonOverlappingPaths(3)
                .withAtLeastNIslsBetweenNeighbouringSwitches(2)
                .random()

        and: "Create a flow going through these switches"
        def flow1 = flowFactory.getRandom(switchPair, false)
        def initialFlowIsls = flow1.retrieveAllEntityPaths().getInvolvedIsls()

        and: "Make each alternative path less preferable than the first flow path"
        def altPaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { !it.containsAll(initialFlowIsls)}

        def flow1PathCost = islHelper.getCost(initialFlowIsls) + diversityIslCost + diversitySwitchCost * 2
        altPaths.each { altPath ->
            def altPathCost = islHelper.getCost(altPath) + diversitySwitchCost * 2
            int difference = flow1PathCost - altPathCost
            def firstAltPathIsl = altPath[0]
            int firstAltPathIslCost = islHelper.getCost([firstAltPathIsl])
            islHelper.updateIslsCost([firstAltPathIsl], (firstAltPathIslCost + Math.abs(difference) + 1))
        }

        when: "Create the second flow with diversity enabled"
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build().create()
        def flow2Path = flow2.retrieveAllEntityPaths()

        then: "The flow is built through the most preferable path (path of the first flow)"
        flow2Path.getInvolvedIsls() == initialFlowIsls

        when: "Create the third flow with diversity enabled"
        def flow3 = flowFactory.getBuilder(switchPair, false, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))
                .withDiverseFlow(flow2.flowId).build()
                .create()
        def flow3Path = flow3.retrieveAllEntityPaths()

        then: "The flow is built through one of alternative paths because they are preferable already"
        def involvedIsls = [flow2Path, flow3Path].collectMany {path -> path.getInvolvedIsls() }
        flow3Path.getInvolvedIsls() != flow2Path.getInvolvedIsls()
        involvedIsls.unique(false) == involvedIsls
    }

    def "Able to get flow paths with correct overlapping segments stats (casual flows)"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(3).random()

        and: "Create three flows with diversity enabled"
        def flow1 = flowFactory.getRandom(switchPair, false)
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build().create()
        def flow3 = flowFactory.getBuilder(switchPair, false, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))
                .withDiverseFlow(flow2.flowId).build()
                .create()

        when: "Get flow path for all flows"
        FlowEntityPath flow1Path, flow2Path, flow3Path
        withPool {
            (flow1Path, flow2Path, flow3Path) = [flow1, flow2, flow3].collectParallel { flow -> flow.retrieveAllEntityPaths() }
        }
        then: "Flow path response for all flows has correct overlapping segments stats"
        verifySegmentsStats([flow1Path, flow2Path, flow3Path],
                expectedThreeFlowsPathIntersectionValuesMap(flow1Path, flow2Path, flow3Path))
    }

    def "Able to get flow paths with correct overlapping segments stats (casual + single-switch flows)"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        and: "Create a casual flow going through these switches"
        def flow1 = flowFactory.getRandom(switchPair, false)

        and: "Create a single-switch with diversity enabled on the source switch of the first flow"
        def flow2 = flowFactory.getBuilder(switchPair.src, switchPair.src, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build()
                .create()

        and: "Create a single-switch with diversity enabled on the destination switch of the first flow"
        def flow3 = flowFactory.getBuilder(switchPair.dst, switchPair.dst, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow2.flowId).build()
                .create()

        when: "Get flow path for all flows"
        FlowEntityPath flow1Path, flow2Path, flow3Path
        withPool {
            (flow1Path, flow2Path, flow3Path) = [flow1, flow2, flow3].collectParallel { flow -> flow.retrieveAllEntityPaths() }
        }

        then: "Flow path response for all flows has correct overlapping segments stats"
        verifySegmentsStats([flow1Path, flow2Path, flow3Path],
                expectedThreeFlowsPathIntersectionValuesMap(flow1Path, flow2Path, flow3Path))

        cleanup:
        //https://github.com/telstra/open-kilda/issues/5221
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.toList()*.getDpId())
    }

    @Tags([LOW_PRIORITY])
    def "Able to update flow to become diverse and single-switch"() {
        given: "Both switch pairs are with the same source switch"
        def switchPair1 = switchPairs.all().neighbouring().random()
        def switchPair2 = switchPairs.all().neighbouring().excludePairs([switchPair1]).includeSourceSwitch(switchPair1.src).random()

        and: "Create two flows starting from the same switch"
        def flow1 = flowFactory.getRandom(switchPair1, false)
        def flow2 = flowFactory.getRandom(switchPair2, false, UP, flow1.occupiedEndpoints())

        when: "Update the second flow to become diverse and single-switch"
       def updatedFlow2 = flow2.update(flow2.deepCopy().tap {
            it.diverseWith = [flow1.flowId]
            it.destination.switchId = switchPair1.src.dpId
            it.destination.portNumber = getRandomAvailablePort(switchPair1.src, topologyDefinition, false,
                    [flow1.source.portNumber, flow2.source.portNumber])
        })
        updatedFlow2.waitForHistoryEvent(FlowActionType.UPDATE)

        then: "Update response contains information about diverse flow"
        updatedFlow2.diverseWith == [flow1.flowId] as Set
        updatedFlow2.source.switchId == updatedFlow2.destination.switchId
    }

    @Deprecated //there is a v2 version
    @Tags([LOW_PRIORITY])
    def "Able to create diverse flows [v1 api]"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(3).random()

        when: "Create three flows with diversity enabled"
        def flow1 = flowFactory.getRandomV1(switchPair, false)
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withDiverseFlow(flow1.flowId).build()
                .createV1()
        def flow3 = flowFactory.getBuilder(switchPair, false, (flow1.occupiedEndpoints() + flow2.occupiedEndpoints()))
                .withDiverseFlow(flow2.flowId).build()
                .createV1()

        then: "All flows have diverse flow IDs in response"
        flow1.retrieveDetailsV1().diverseWith.sort() == [flow2.flowId, flow3.flowId].sort()
        flow2.retrieveDetailsV1().diverseWith.sort() == [flow1.flowId, flow3.flowId].sort()
        flow3.retrieveDetailsV1().diverseWith.sort() == [flow1.flowId, flow2.flowId].sort()

        and: "All flows have different paths"
        def allInvolvedIsls = [flow1, flow2, flow3].collectMany {flow ->
            flow.retrieveAllEntityPaths().getInvolvedIsls()
        }
        allInvolvedIsls.unique(false) == allInvolvedIsls
    }

    void verifySegmentsStats(List<FlowEntityPath> flowPaths, Map expectedValuesMap) {
        flowPaths.flowPath.each { flow ->
            with(flow.path.diverseGroup) { diverseGroup ->
                verifyAll(diverseGroup.overlappingSegments) {
                    it == expectedValuesMap["diverseGroup"][flow.flowId]
                }
                with(diverseGroup.otherFlows) { otherFlows ->
                    assert (flowPaths.flowPath*.flowId - flow.flowId).containsAll(otherFlows*.id)
                    otherFlows.each { otherFlow ->
                        verifyAll(otherFlow.segmentsStats) {
                            it == expectedValuesMap["otherFlows"][flow.flowId][otherFlow.id]
                        }
                    }
                }
            }
        }
    }

    def expectedThreeFlowsPathIntersectionValuesMap(FlowEntityPath flow1Path,
                                                    FlowEntityPath flow2Path,
                                                    FlowEntityPath flow3Path) {
        Path flow1ForwardPath, flow2ForwardPath, flow3ForwardPath
        withPool {
            (flow1ForwardPath, flow2ForwardPath, flow3ForwardPath) = [flow1Path, flow2Path, flow3Path]
                    .collectParallel { it.flowPath.path.forward }
        }

        return [
                diverseGroup: [
                        (flow1Path.flowPath.flowId): flow1ForwardPath.overlappingSegmentStats([flow2ForwardPath, flow3ForwardPath]),
                        (flow2Path.flowPath.flowId): flow2ForwardPath.overlappingSegmentStats([flow1ForwardPath, flow3ForwardPath]),
                        (flow3Path.flowPath.flowId): flow3ForwardPath.overlappingSegmentStats([flow1ForwardPath, flow2ForwardPath])
                ],
                otherFlows  : [
                        (flow1Path.flowPath.flowId): [
                                (flow2Path.flowPath.flowId): flow1ForwardPath.overlappingSegmentStats([flow2ForwardPath]),
                                (flow3Path.flowPath.flowId): flow1ForwardPath.overlappingSegmentStats([flow3ForwardPath]),
                        ],
                        (flow2Path.flowPath.flowId): [
                                (flow1Path.flowPath.flowId): flow2ForwardPath.overlappingSegmentStats([flow1ForwardPath]),

                                (flow3Path.flowPath.flowId): flow2ForwardPath.overlappingSegmentStats([flow3ForwardPath]),
                        ],
                        (flow3Path.flowPath.flowId): [
                                (flow1Path.flowPath.flowId): flow3ForwardPath.overlappingSegmentStats([flow1ForwardPath]),
                                (flow2Path.flowPath.flowId): flow3ForwardPath.overlappingSegmentStats([flow2ForwardPath])
                        ]
                ]
        ]
    }
}
