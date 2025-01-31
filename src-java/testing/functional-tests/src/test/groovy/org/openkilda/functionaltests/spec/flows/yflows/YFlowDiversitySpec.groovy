package org.openkilda.functionaltests.spec.flows.yflows

import org.openkilda.functionaltests.helpers.factory.FlowFactory

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowWithSubFlowsEntityPath
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

@Slf4j
@Narrative("Verify the ability to create diverse y-flows in the system.")
class YFlowDiversitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory
    @Autowired
    @Shared
    FlowFactory flowFactory

    def "Able to create diverse Y-Flows"() {
        given: "Switches with three not overlapping paths at least"
        def swT = switchTriplets.all(false, false).withAllDifferentEndpoints()
                .withAtLeastNNonOverlappingPaths(4).random()
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three Y-Flows with diversity enabled"
        def yFlow1 = yFlowFactory.getRandom(swT, false)

        def yFlow2 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build().create()

        def yFlow3 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints() + yFlow2.occupiedEndpoints())
                .withDiverseFlow(yFlow2.yFlowId).build().create()
        then: "Y-Flow create response contains info about diverse yFlow"
        !yFlow1.diverseWithYFlows
        yFlow2.diverseWithYFlows.sort() == [yFlow1.yFlowId].sort()
        yFlow3.diverseWithYFlows.sort() == [yFlow1.yFlowId, yFlow2.yFlowId].sort()

        and: "All Y-Flows have diverse yFlow IDs in response"
        yFlow1.retrieveDetails().diverseWithYFlows.sort() == [yFlow2.yFlowId, yFlow3.yFlowId].sort()
        yFlow2.retrieveDetails().diverseWithYFlows.sort() == [yFlow1.yFlowId, yFlow3.yFlowId].sort()
        yFlow3.retrieveDetails().diverseWithYFlows.sort() == [yFlow1.yFlowId, yFlow2.yFlowId].sort()


        and: "All Y-Flows have different paths"
        def allInvolvedIsls = [yFlow1, yFlow2, yFlow3].collectMany { yFlow ->
           yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == yFlow.subFlows.first().flowId }.getInvolvedIsls()

        }
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Y-Flows histories contains 'diverse' information"
        [yFlow2, yFlow3].each {//yFlow1 had no diversity at the time of creation
            assert  it.retrieveSubFlowHistory(it.subFlows.first().flowId).getEntriesByType(FlowActionType.CREATE).first().dumps
                    .find { it.type == "stateAfter" }?.diverseGroupId
        }

        and: "Y-Flow passes flow validation"
        withPool {
            [yFlow1, yFlow2, yFlow3].eachParallel { yFlow -> assert yFlow.validate().asExpected }
        }

        when: "Delete Y-Flows"
        withPool {
            [yFlow1, yFlow2, yFlow3].each { yFlow -> yFlow.delete() }
        }

        then: "Y-Flows' histories contain 'diverseGroupId' information in 'delete' operation"
        verifyAll(yFlow1.retrieveSubFlowHistory(yFlow1.subFlows[0].flowId).getEntriesByType(FlowActionType.DELETE).first().dumps) {
            it.find { it.type == "stateBefore" }?.diverseGroupId
            !it.find { it.type == "stateAfter" }?.diverseGroupId
        }
    }

    def "Able to update Y-Flow to became diverse with simple multiSwitch flow"() {
        given: "Switches with two not overlapping paths at least"
        def swT = switchTriplets.all().withAtLeastNNonOverlappingPaths(2).random()
        assumeTrue(swT != null, "Unable to find suitable switches")

        and: "Y-Flow created"
        def yFlow = yFlowFactory.getRandom(swT, false)

        and: "Simple multiSwitch flow on the same path as first sub-flow"
        def flow = flowFactory.getRandom(swT.shared, swT.ep1, false)
        def subFlowId = yFlow.subFlows.first().flowId
        def involvedIslSubFlow = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == subFlowId }.getInvolvedIsls()
        def involvedIslSimpleFlow = flow.retrieveAllEntityPaths().getInvolvedIsls()
        assert involvedIslSubFlow == involvedIslSimpleFlow

        when: "Update Y-Flow to become diverse with simple multiSwitch flow"
        def updateRequest = yFlow.convertToUpdate().tap { it.diverseFlowId = flow.flowId }
        def updateResponse = yFlow.sendUpdateRequest(updateRequest)
        yFlow = yFlow.waitForBeingInState(FlowState.UP)

        then: "Update response contains information about diverse flow"
        updateResponse.diverseWithFlows.sort() == [flow.flowId].sort()

        and: "Y-Flow has been successfully updated with appropriate diverse info"
        verifyAll {
            yFlow.diverseWithFlows.sort() == [flow.flowId].sort()
            yFlow.diverseWithYFlows.empty
            yFlow.diverseWithHaFlows.empty
        }

        and: "Simple multi switch flow has the 'diverse_with' field"
        with(flow.retrieveDetails()) {
            it.diverseWithYFlows.sort() == [yFlow.yFlowId].sort()
            it.diverseWith.empty
        }

        and: "First sub-flow became diverse and changed path"
        def involvedIslSubFlowAfterUpdate = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == subFlowId }.getInvolvedIsls()
        assert involvedIslSubFlowAfterUpdate != involvedIslSimpleFlow

        and: "First sub flow history contains 'groupId' information"
        verifyAll(yFlow.retrieveSubFlowHistory(subFlowId).getEntriesByType(FlowActionType.UPDATE).first().dumps) {
            !it.find { it.type == "stateBefore" }?.diverseGroupId
            it.find { it.type == "stateAfter" }?.diverseGroupId
        }

        and: "Y-Flow passes flow validation"
        assert yFlow.validate().asExpected

        and: "Simple flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Partially update Y-Flow to become not diverse with simple multiSwitch flow"
        def patchRequest = YFlowPatchPayload.builder().diverseFlowId("").build()
        def patchResponse = yFlow.sendPartialUpdateRequest(patchRequest)
        yFlow = yFlow.waitForBeingInState(FlowState.UP)

        then: "Update response contains information about diverse flow"
        patchResponse.diverseWithFlows.empty

        and: "Y-Flow is really updated"
        yFlow.diverseWithFlows.empty

        and: "Simple multi switch flow doesn't have the 'diverse_with' field"
        flow.retrieveDetails().diverseWithYFlows.empty
    }

    def "Able to create Y-Flow with one switch sub flow and diverse with simple multiSwitch flow"() {
        given: "Two switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "Simple multiSwitch flow"
        def flow = flowFactory.getRandom(switchPair.src, switchPair.dst, false)

        when: "Create a Y-Flow with one switch sub flow and diversity with simple flow"
        def swT = switchTriplets.all(true, true)
                .findSpecificSwitchTriplet(switchPair.src.dpId, switchPair.src.dpId, switchPair.dst.dpId)
        def yFlow = yFlowFactory.getBuilder(swT, false).withDiverseFlow(flow.flowId).build().create()

        then: "Create response contains information about diverse flow"
        yFlow.diverseWithFlows == [flow.flowId] as Set
        yFlow.diverseWithYFlows.empty
        yFlow.diverseWithHaFlows.empty

        and: "Y-Flow is diverse with Flow"
        yFlow.diverseWithFlows == [flow.flowId] as Set

        and: "Flow is diverse with Y-Flow"
        with(flow.retrieveDetails()) {
            it.diverseWithYFlows == [yFlow.yFlowId] as Set
            it.diverseWith.empty
            it.diverseWithHaFlows.empty
        }

        and: "Y-Flow passes flow validation"
        yFlow.validate().asExpected

        and: "Simple Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    def "Able to get Y-Flow paths with correct overlapping segments stats"() {
        given: "Switches with three not overlapping paths at least"
        def swT = switchTriplets.all().withAtLeastNNonOverlappingPaths(3).random()

        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three Y-Flows with diversity enabled"
        def yFlow1 = yFlowFactory.getRandom(swT, false)

        def yFlow2 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build().create()

        def yFlow3 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints() + yFlow2.occupiedEndpoints())
                .withDiverseFlow(yFlow2.yFlowId).build().create()

        and: "Get flow path for all y-flows"
        FlowWithSubFlowsEntityPath yFlow1Paths, yFlow2Paths, yFlow3Paths
        withPool {
            (yFlow1Paths, yFlow2Paths, yFlow3Paths) = [yFlow1, yFlow2, yFlow3]
                    .collectParallel { yFlow -> yFlow.retrieveAllEntityPaths() }
        }

        /* https://github.com/telstra/open-kilda/issues/5191
        and: "All y-flow paths have diverse group information"
        def yFlow1ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow1Paths, yFlow2Paths, yFlow3Paths)
        verifySegmentsStats(yFlow1Paths, yFlow1ExpectedValues)

        def yFlow2ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow2Paths, yFlow1Paths, yFlow3Paths)
        verifySegmentsStats(yFlow2Paths, yFlow2ExpectedValues)

        def yFlow3ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow3Paths, yFlow1Paths, yFlow2Paths)
        verifySegmentsStats(yFlow3Paths, yFlow3ExpectedValues)

        then: "Get flow path for sub-flows"
        //TODO Need to update after implementing a new approach to interact with a regular flow
        FlowPathPayload yFlow1s1Path, yFlow1s2Path, yFlow2s1Path, yFlow2s2Path, yFlow3s1Path, yFlow3s2Path
        withPool {
            (yFlow1s1Path, yFlow1s2Path, yFlow2s1Path, yFlow2s2Path, yFlow3s1Path, yFlow3s2Path) =
                    [yFlow1.subFlows[0], yFlow1.subFlows[1],
                     yFlow2.subFlows[0], yFlow2.subFlows[1],
                     yFlow3.subFlows[0], yFlow3.subFlows[1]].collectParallel { northbound.getFlowPath(it.flowId) }
        }
        verifySegmentsStats([yFlow1s1Path, yFlow1s2Path], yFlow1ExpectedValues)
        verifySegmentsStats([yFlow2s1Path, yFlow2s2Path], yFlow2ExpectedValues)
        verifySegmentsStats([yFlow3s1Path, yFlow3s2Path], yFlow3ExpectedValues)
        */

        then: "All Y-Flow paths have diverse group information"
        verifyAll {
            yFlow1Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow2, yFlow3])
            yFlow2Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow1, yFlow3])
            yFlow3Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow1, yFlow2])
        }
    }

    @Tags([LOW_PRIORITY])
    def "Able to get Y-Flow paths with correct overlapping segments stats with one-switch Y-Flow"() {
        given: "Three switches"
        def swT = switchTriplets.all().withAllDifferentEndpoints().random()
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create y-flow"
        def yFlow1 = yFlowFactory.getRandom(swT, false)

        and: "Create one-switch y-flows on shared and ep1 switches in the same diversity group"
        def swTAllAsSharedSw = switchTriplets.all(true, true)
                .findSpecificSwitchTriplet(swT.shared.dpId, swT.shared.dpId, swT.shared.dpId)
        def yFlow2 = yFlowFactory.getBuilder(swTAllAsSharedSw, false, yFlow1.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build().create()

        def swTAllAsEp1 = switchTriplets.all(true, true)
                .findSpecificSwitchTriplet(swT.ep1.dpId, swT.ep1.dpId, swT.ep1.dpId)
        def yFlow3 = yFlowFactory.getBuilder(swTAllAsEp1, false, yFlow1.occupiedEndpoints() + yFlow2.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build().create()

        and: "Get flow paths for all y-flows"
        FlowWithSubFlowsEntityPath yFlow1Paths, yFlow2Paths, yFlow3Paths
        withPool {
            (yFlow1Paths, yFlow2Paths, yFlow3Paths) = [yFlow1, yFlow2, yFlow3]
                    .collectParallel { yFlow -> yFlow.retrieveAllEntityPaths() }
        }

        then: 'All YFlows have correct diverse group information'
        /* https://github.com/telstra/open-kilda/issues/5191
        def yFlow1ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow1Paths, yFlow2Paths, yFlow3Paths)
        def yFlow2ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow2Paths, yFlow1Paths, yFlow3Paths)
        def yFlow3ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow3Paths, yFlow1Paths, yFlow2Paths)

        verifySegmentsStats(yFlow3Paths, yFlow3ExpectedValues)
        verifySegmentsStats(yFlow2Paths, yFlow2ExpectedValues)
        verifySegmentsStats(yFlow1Paths, yFlow1ExpectedValues)
        */
        verifyAll {
            yFlow1Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow2, yFlow3])
            yFlow2Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow1, yFlow3])
            yFlow3Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow1, yFlow2])
        }
    }

    def "Able to get Y-Flow paths with with diversity part when flows become diverse after partial update"() {
        given: "Switches with three not overlapping paths at least"
        def swT = switchTriplets.all().withAtLeastNNonOverlappingPaths(3).random()
        assumeTrue(swT != null, "Unable to find suitable switches")

        and: "Create two Y-Flows"
        def yFlow1 = yFlowFactory.getRandom(swT, false)
        def yFlow2 = yFlowFactory.getRandom(swT, false)

        when: "Partially update Y-Flow to become diverse with another one"
        yFlow1 = yFlow1.partialUpdate(YFlowPatchPayload.builder().diverseFlowId(yFlow2.yFlowId).build())

        and: "Y-Flow has been updated successfully and become part of diverse group with another Y-Flow"
        assert yFlow1.diverseWithYFlows == [yFlow2.yFlowId] as Set

        and: "Request paths for both Y-Flows"
        FlowWithSubFlowsEntityPath yFlow1Paths, yFlow2Paths
        withPool {
            (yFlow1Paths, yFlow2Paths) = [yFlow1, yFlow2]
                    .collectParallel { yFlow -> yFlow.retrieveAllEntityPaths() }
        }

        then: "Path request contain all the subflows in diverse group"
        verifyAll {
            yFlow1Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow2])
            yFlow2Paths.subFlowPaths.first().path.diverseGroup.otherFlows*.id.sort() == getSubFlowsId([yFlow1])
        }
    }

    void verifySegmentsStats(FlowWithSubFlowsEntityPath yFlowPaths, Map expectedValuesMap) {
        yFlowPaths.subFlowPaths.each { flow ->
            flow.path.diverseGroup && with(flow.path.diverseGroup) { diverseGroup ->
                verifyAll(diverseGroup.overlappingSegments) {
                    it == expectedValuesMap["diverseGroup"][flow.flowId]
                }
                with(diverseGroup.otherFlows) { otherFlows ->
                    otherFlows.each { otherFlow ->
                        verifyAll(otherFlow.segmentsStats) {
                            it == expectedValuesMap["otherFlows"][flow.flowId][otherFlow.id]
                        }
                    }
                }
            }
        }
    }

    void verifySegmentsStats(List<FlowPathPayload> flowPaths, Map expectedValuesMap) {
        flowPaths.each { flow ->
            with(flow.diverseGroupPayload) { diverseGroup ->
                verifyAll(diverseGroup.overlappingSegments) {
                    it == expectedValuesMap["diverseGroup"][flow.id]
                }
                with(diverseGroup.otherFlows) { otherFlows ->
                    otherFlows.each { otherFlow ->
                        verifyAll(otherFlow.segmentsStats) {
                            it == expectedValuesMap["otherFlows"][flow.id][otherFlow.id]
                        }
                    }
                }
            }
        }
    }

    def expectedThreeYFlowsPathIntersectionValuesMap(FlowWithSubFlowsEntityPath yFlowUnderTest,
                                                     FlowWithSubFlowsEntityPath yFlow2Paths,
                                                     FlowWithSubFlowsEntityPath yFlow3Paths) {
        return [
                diverseGroup: [
                        (yFlowUnderTest.subFlowPaths[0].flowId): yFlowUnderTest.subFlowPaths[0].path.forward.overlappingSegmentStats(
                                [yFlow2Paths.subFlowPaths[0].path.forward,
                                 yFlow2Paths.subFlowPaths[1].path.forward,
                                 yFlow3Paths.subFlowPaths[0].path.forward,
                                 yFlow3Paths.subFlowPaths[1].path.forward]
                        ),
                        (yFlowUnderTest.subFlowPaths[1].flowId): yFlowUnderTest.subFlowPaths[1].path.forward.overlappingSegmentStats(
                                [yFlow2Paths.subFlowPaths[0].path.forward,
                                 yFlow2Paths.subFlowPaths[1].path.forward,
                                 yFlow3Paths.subFlowPaths[0].path.forward,
                                 yFlow3Paths.subFlowPaths[1].path.forward]
                        )
                ],
                otherFlows  : [
                        (yFlowUnderTest.subFlowPaths[0].flowId): [
                                (yFlow2Paths.subFlowPaths[0].flowId): yFlowUnderTest.subFlowPaths[0].path.forward.overlappingSegmentStats([yFlow2Paths.subFlowPaths[0].path.forward]),
                                (yFlow2Paths.subFlowPaths[1].flowId): yFlowUnderTest.subFlowPaths[0].path.forward.overlappingSegmentStats([yFlow2Paths.subFlowPaths[1].path.forward]),
                                (yFlow3Paths.subFlowPaths[0].flowId): yFlowUnderTest.subFlowPaths[0].path.forward.overlappingSegmentStats([yFlow3Paths.subFlowPaths[0].path.forward]),
                                (yFlow3Paths.subFlowPaths[1].flowId): yFlowUnderTest.subFlowPaths[0].path.forward.overlappingSegmentStats([yFlow3Paths.subFlowPaths[1].path.forward]),
                        ],
                        (yFlowUnderTest.subFlowPaths[1].flowId): [
                                (yFlow2Paths.subFlowPaths[0].flowId): yFlowUnderTest.subFlowPaths[1].path.forward.overlappingSegmentStats([yFlow2Paths.subFlowPaths[0].path.forward]),
                                (yFlow2Paths.subFlowPaths[1].flowId): yFlowUnderTest.subFlowPaths[1].path.forward.overlappingSegmentStats([yFlow2Paths.subFlowPaths[1].path.forward]),
                                (yFlow3Paths.subFlowPaths[0].flowId): yFlowUnderTest.subFlowPaths[1].path.forward.overlappingSegmentStats([yFlow3Paths.subFlowPaths[0].path.forward]),
                                (yFlow3Paths.subFlowPaths[1].flowId): yFlowUnderTest.subFlowPaths[1].path.forward.overlappingSegmentStats([yFlow3Paths.subFlowPaths[1].path.forward])
                        ]
                ]
        ]
    }

    List<String> getSubFlowsId(List<YFlowExtended> yFlows) {
        return yFlows.subFlows.collect { it.flowId }.flatten().sort()
    }
}
