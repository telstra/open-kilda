package org.openkilda.functionaltests.spec.flows.yflows

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowWithSubFlowsEntityPath
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify the ability to create diverse y-flows in the system.")
class YFlowDiversitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    def "Able to create diverse Y-Flows"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three Y-Flows with diversity enabled"
        def yFlow1 = yFlowFactory.getRandom(swT, false)

        def yFlow2 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build()
        yFlow2 = yFlow2.waitForBeingInState(FlowState.UP)

        def yFlow3 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints() + yFlow2.occupiedEndpoints())
                .withDiverseFlow(yFlow2.yFlowId).build()
        yFlow3 = yFlow3.waitForBeingInState(FlowState.UP)

        then: "Y-Flow create response contains info about diverse yFlow"
        !yFlow1.diverseWithYFlows
        yFlow2.diverseWithYFlows.sort() == [yFlow1.yFlowId].sort()
        yFlow3.diverseWithYFlows.sort() == [yFlow1.yFlowId, yFlow2.yFlowId].sort()

        and: "All Y-Flows have diverse yFlow IDs in response"
        yFlow1.retrieveDetails().diverseWithYFlows.sort() == [yFlow2.yFlowId, yFlow3.yFlowId].sort()
        yFlow2.retrieveDetails().diverseWithYFlows.sort() == [yFlow1.yFlowId, yFlow3.yFlowId].sort()
        yFlow3.retrieveDetails().diverseWithYFlows.sort() == [yFlow1.yFlowId, yFlow2.yFlowId].sort()


        and: "All Y-Flows have different paths"
        def allInvolvedIsls = [yFlow1.subFlows[0], yFlow2.subFlows[0], yFlow3.subFlows[0]].collectMany {
            pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(it.flowId)))
        }
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Y-Flows histories contains 'diverse' information"
        [yFlow2.subFlows.first(), yFlow3.subFlows.first()].each {//yFlow1 had no diversity at the time of creation
            assert flowHelper.getEarliestHistoryEntryByAction(it.flowId, FlowActionType.CREATE.value).dumps
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
        def yFlowsAreDeleted = true

        then: "Y-Flows' histories contain 'diverseGroupId' information in 'delete' operation"
        verifyAll(flowHelper.getEarliestHistoryEntryByAction(yFlow1.subFlows[0].flowId,  FlowActionType.DELETE.value).dumps) {
            it.find { it.type == "stateBefore" }?.diverseGroupId
            !it.find { it.type == "stateAfter" }?.diverseGroupId
        }

        cleanup:
        !yFlowsAreDeleted && [yFlow1, yFlow2, yFlow3].each { yFlow -> yFlow && yFlow.delete() }
    }

    def "Able to update Y-Flow to became diverse with simple multiSwitch flow"() {
        given: "Switches with two not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    it.pathsEp1.collect { pathHelper.getInvolvedIsls(it) }
                            .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        and: "Y-Flow created"
        def yFlow = yFlowFactory.getRandom(swT, false)

        and: "Simple multiSwitch flow on the same path as first sub-flow"
        def flow = flowHelperV2.randomFlow(swT.shared, swT.ep1, false)
        flowHelperV2.addFlow(flow)
        def subFlowId = yFlow.subFlows.first().flowId
        def involvedIslSubFlow = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == subFlowId }.getInvolvedIsls()
        def involvedIslSimpleFlow = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow.flowId)))
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
        with(northboundV2.getFlow(flow.flowId)) {
            it.diverseWithYFlows.sort() == [yFlow.yFlowId].sort()
            it.diverseWith.empty
        }

        and: "First sub-flow became diverse and changed path"
        def involvedIslSubFlowAfterUpdate = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == subFlowId }.getInvolvedIsls()
        assert involvedIslSubFlowAfterUpdate != involvedIslSimpleFlow

        and: "First sub flow history contains 'groupId' information"
        verifyAll(flowHelper.getEarliestHistoryEntryByAction(subFlowId, FlowActionType.UPDATE_ACTION.value).dumps) {
            !it.find { it.type == "stateBefore" }?.diverseGroupId
            it.find { it.type == "stateAfter" }?.diverseGroupId
        }

        and: "Y-Flow passes flow validation"
        assert yFlow.validate().asExpected

        and: "Simple flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Partially update Y-Flow to become not diverse with simple multiSwitch flow"
        def patchRequest = YFlowPatchPayload.builder().diverseFlowId("").build()
        def patchResponse = yFlow.sendPartialUpdateRequest(patchRequest)
        yFlow = yFlow.waitForBeingInState(FlowState.UP)

        then: "Update response contains information about diverse flow"
        patchResponse.diverseWithFlows.empty

        and: "Y-Flow is really updated"
        yFlow.diverseWithFlows.empty

        and: "Simple multi switch flow doesn't have the 'diverse_with' field"
        northboundV2.getFlow(flow.flowId).diverseWithYFlows.empty

        cleanup:
        yFlow && yFlow.delete()
    }

    def "Able to create Y-Flow with one switch sub flow and diverse with simple multiSwitch flow"() {
        given: "Two switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "Simple multiSwitch flow"
        def flow = flowHelperV2.randomFlow(switchPair.src, switchPair.dst, false)
        flowHelperV2.addFlow(flow)

        when: "Create a Y-Flow with one switch sub flow and diversity with simple flow"
        def swT = topologyHelper.getSwitchTriplet(switchPair.src.dpId, switchPair.src.dpId, switchPair.dst.dpId)
        def yFlowCreatedResponse = yFlowFactory.getBuilder(swT, false).withDiverseFlow(flow.flowId).build()
        def yFlow = yFlowCreatedResponse.waitForBeingInState(FlowState.UP)

        then: "Create response contains information about diverse flow"
        yFlowCreatedResponse.diverseWithFlows == [flow.flowId] as Set
        yFlowCreatedResponse.diverseWithYFlows.empty
        yFlowCreatedResponse.diverseWithHaFlows.empty

        and: "Y-Flow is diverse with Flow"
        yFlow.diverseWithFlows == [flow.flowId] as Set

        and: "Flow is diverse with Y-Flow"
        with(northboundV2.getFlow(flow.flowId)) {
            it.diverseWithYFlows == [yFlow.yFlowId] as Set
            it.diverseWith.empty
            it.diverseWithHaFlows.empty
        }

        and: "Y-Flow passes flow validation"
        yFlow.validate().asExpected

        and: "Simple Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        cleanup:
        yFlow && yFlow.delete()
    }

    def "Able to get Y-Flow paths with correct overlapping segments stats"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three Y-Flows with diversity enabled"
        def yFlow1 = yFlowFactory.getRandom(swT, false)

        def yFlow2 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints()).withDiverseFlow(yFlow1.yFlowId).build()
        yFlow2 = yFlow2.waitForBeingInState(FlowState.UP)

        def yFlow3 = yFlowFactory.getBuilder(swT, false, yFlow1.occupiedEndpoints() + yFlow2.occupiedEndpoints())
                .withDiverseFlow(yFlow2.yFlowId).build()
        yFlow3 = yFlow3.waitForBeingInState(FlowState.UP)

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

        cleanup:
        withPool {
            [yFlow1, yFlow2, yFlow3].eachParallel { yFlow -> yFlow && yFlow.delete() }
        }
    }

    @Tags([LOW_PRIORITY])
    def "Able to get Y-Flow paths with correct overlapping segments stats with one-switch Y-Flow"() {
        given: "Three switches"
        def swT = topologyHelper.switchTriplets.find(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT)
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create y-flow"
        def yFlow1 = yFlowFactory.getRandom(swT, false)

        and: "Create one-switch y-flows on shared and ep1 switches in the same diversity group"
        def swTAllAsSharedSw = topologyHelper.getSwitchTriplet(swT.shared.dpId, swT.shared.dpId, swT.shared.dpId)
        def yFlowRequest2 = yFlowFactory.getBuilder(swTAllAsSharedSw, false, yFlow1.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build()
        def yFlow2 = yFlowRequest2.waitForBeingInState(FlowState.UP)

        def swTAllAsEp1 = topologyHelper.getSwitchTriplet(swT.ep1.dpId, swT.ep1.dpId, swT.ep1.dpId)
        def yFlowRequest3 = yFlowFactory.getBuilder(swTAllAsEp1, false, yFlow1.occupiedEndpoints() + yFlow2.occupiedEndpoints())
                .withDiverseFlow(yFlow1.yFlowId).build()
        def yFlow3 = yFlowRequest3.waitForBeingInState(FlowState.UP)

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

        cleanup:
        withPool {
            [yFlow1, yFlow2, yFlow3].eachParallel { yFlow -> yFlow && yFlow.delete() }
        }
    }

    def "Able to get Y-Flow paths with with diversity part when flows become diverse after partial update"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
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

        cleanup:
        withPool {
            [yFlow1, yFlow2].eachParallel { yFlow -> yFlow && yFlow.delete() }
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
