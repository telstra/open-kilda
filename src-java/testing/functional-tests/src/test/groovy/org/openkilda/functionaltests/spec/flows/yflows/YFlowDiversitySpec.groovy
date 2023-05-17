package org.openkilda.functionaltests.spec.flows.yflows

import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.northbound.dto.v2.yflows.YFlow

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.UPDATE_ACTION

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify the ability to create diverse y-flows in the system.")
class YFlowDiversitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "Able to create diverse y-flows"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three y-flows with diversity enabled"
        def yFlowRequest1 = yFlowHelper.randomYFlow(swT, false)
        def yFlow1 = yFlowHelper.addYFlow(yFlowRequest1)
        def yFlowRequest2 = yFlowHelper.randomYFlow(swT, false, [yFlowRequest1])
                .tap { it.diverseFlowId = yFlow1.YFlowId }
        def yFlow2 = yFlowHelper.addYFlow(yFlowRequest2)
        def yFlowRequest3 = yFlowHelper.randomYFlow(swT, false, [yFlowRequest1, yFlowRequest2])
                .tap { it.diverseFlowId = yFlow2.YFlowId }
        def yFlow3 = yFlowHelper.addYFlow(yFlowRequest3)

        then: "YFlow create response contains info about diverse yFlow"
        !yFlow1.diverseWithYFlows
        yFlow2.diverseWithYFlows.sort() == [yFlow1.YFlowId].sort()
        yFlow3.diverseWithYFlows.sort() == [yFlow1.YFlowId, yFlow2.YFlowId].sort()

        and: "All y-flows have diverse yFlow IDs in response"
        northboundV2.getYFlow(yFlow1.YFlowId).diverseWithYFlows.sort() == [yFlow2.YFlowId, yFlow3.YFlowId].sort()
        northboundV2.getYFlow(yFlow2.YFlowId).diverseWithYFlows.sort() == [yFlow1.YFlowId, yFlow3.YFlowId].sort()
        northboundV2.getYFlow(yFlow3.YFlowId).diverseWithYFlows.sort() == [yFlow1.YFlowId, yFlow2.YFlowId].sort()


        and: "All y-flows have different paths"
        def allInvolvedIsls = [yFlow1.subFlows[0], yFlow2.subFlows[0], yFlow3.subFlows[0]].collectMany {
            pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(it.flowId)))
        }
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "YFlows histories contains 'diverse?' information"
        [yFlow2.subFlows[0], yFlow3.subFlows[0]].each {//flow1 had no diversity at the time of creation
            assert northbound.getFlowHistory(it.flowId).find { it.action == CREATE_ACTION }.dumps
                    .find { it.type == "stateAfter" }?.diverseGroupId
        }

        and: "Y-flow passes flow validation"
        [yFlow1, yFlow2, yFlow3].each { assert northboundV2.validateYFlow(it.YFlowId).asExpected }

        when: "Delete y-flows"
        [yFlow1, yFlow2, yFlow3].each { it && yFlowHelper.deleteYFlow(it.YFlowId) }
        def yFlowsAreDeleted = true

        then: "YFlows' histories contain 'diverseGroupId' information in 'delete' operation"
        verifyAll(northbound.getFlowHistory(yFlow1.subFlows[0].flowId).find { it.action == DELETE_ACTION }.dumps) {
            it.find { it.type == "stateBefore" }?.diverseGroupId
            !it.find { it.type == "stateAfter" }?.diverseGroupId
        }

        cleanup:
        !yFlowsAreDeleted && [yFlow1, yFlow2, yFlow3].each { it && yFlowHelper.deleteYFlow(it.YFlowId) }
    }

    @Tidy
    def "Able to update y-flow to became diverse with simple multiSwitch flow"() {
        given: "Switches with two not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    it.pathsEp1.collect { pathHelper.getInvolvedIsls(it) }
                            .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        and: "Y-flow created"
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        and: "Simple multiSwitch flow on the same path as first sub-flow"
        def flow = flowHelperV2.randomFlow(swT.shared, swT.ep1, false)
        flowHelperV2.addFlow(flow)
        def subFlow = yFlow.subFlows.first()
        def involvedIslSubFlow = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(subFlow.flowId)))
        def involvedIslSimpleFlow = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow.flowId)))
        assert involvedIslSubFlow == involvedIslSimpleFlow

        when: "Update y-flow to become diverse with simple multiSwitch flow"
        def update = yFlowHelper.convertToUpdate(yFlow.tap { it.diverseWithFlows = [flow.flowId] })
        def updateResponse = yFlowHelper.updateYFlow(yFlow.YFlowId, update)

        then: "Update response contains information about diverse flow"
        updateResponse.diverseWithFlows.sort() == [flow.flowId].sort()

        and: "Y-flow is really updated"
        with(northboundV2.getYFlow(yFlow.YFlowId)) {
            it.diverseWithFlows.sort() == [flow.flowId].sort()
            it.diverseWithYFlows.empty
        }

        and: "Simple multi switch flow has the 'diverse_with' field"
        with(northboundV2.getFlow(flow.flowId)) {
            it.diverseWithYFlows.sort() == [yFlow.YFlowId].sort()
            it.diverseWith.empty
        }

        and: "First sub-flow became diverse and changed path"
        def involvedIslSubFlowAfterUpdate = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(subFlow.flowId)))
        assert involvedIslSubFlowAfterUpdate != involvedIslSimpleFlow

        and: "First sub flow history contains 'groupId' information"
        verifyAll(northbound.getFlowHistory(subFlow.flowId).find { it.action == UPDATE_ACTION }.dumps) {
            !it.find { it.type == "stateBefore" }?.diverseGroupId
            it.find { it.type == "stateAfter" }?.diverseGroupId
        }

        and: "Y-flow passes flow validation"
        assert northboundV2.validateYFlow(yFlow.YFlowId).asExpected

        and: "Simple flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Partially update y-flow to become not diverse with simple multiSwitch flow"
        def patch = YFlowPatchPayload.builder().diverseFlowId("").build()
        def patchResponse = yFlowHelper.partialUpdateYFlow(yFlow.YFlowId, patch)

        then: "Update response contains information about diverse flow"
        patchResponse.diverseWithFlows.empty

        and: "Y-flow is really updated"
        northboundV2.getYFlow(yFlow.YFlowId).diverseWithFlows.empty

        and: "Simple multi switch flow doesn't have the 'diverse_with' field"
        northboundV2.getFlow(flow.flowId).diverseWithYFlows.empty

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Able to get y-flow paths with correct overlapping segments stats"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three y-flows with diversity enabled"
        def yFlowRequest1 = yFlowHelper.randomYFlow(swT, false)
        def yFlow1 = yFlowHelper.addYFlow(yFlowRequest1)
        def yFlowRequest2 = yFlowHelper.randomYFlow(swT, false, [yFlowRequest1])
                .tap { it.diverseFlowId = yFlow1.YFlowId }
        def yFlow2 = yFlowHelper.addYFlow(yFlowRequest2)
        def yFlowRequest3 = yFlowHelper.randomYFlow(swT, false, [yFlowRequest1, yFlowRequest2])
                .tap { it.diverseFlowId = yFlow2.YFlowId }
        def yFlow3 = yFlowHelper.addYFlow(yFlowRequest3)

        and: "Get flow path for all y-flows"
        YFlowPaths yFlow1Paths, yFlow2Paths, yFlow3Paths
        withPool {
            (yFlow1Paths, yFlow2Paths, yFlow3Paths) = [yFlow1, yFlow2, yFlow3].collectParallel { northboundV2.getYFlowPaths(it.yFlowId) }
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
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow1Paths, [yFlow2, yFlow3])
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow2Paths, [yFlow1, yFlow3])
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow3Paths, [yFlow2, yFlow1])

        cleanup:
        withPool {
            [yFlow1, yFlow2, yFlow3].eachParallel { it && yFlowHelper.deleteYFlow(it.YFlowId) }
        }

    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Able to get y-flow paths with correct overlapping segments stats with one-switch y-flow"() {
        given: "Three switches"
        def swT = topologyHelper.switchTriplets.find {
            it.shared != it.ep1 && it.shared != it.ep2 && it.ep1 != it.ep2
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create y-flow"
        def yFlowRequest1 = yFlowHelper.randomYFlow(swT, false)
        def yFlow1 = yFlowHelper.addYFlow(yFlowRequest1)

        and: "Create one-switch y-flows on shared and ep1 switches in the same diversity group"
        def yFlowRequest2 = yFlowHelper.singleSwitchYFlow(swT.getShared(), false, [yFlowRequest1])
                .tap { it.diverseFlowId = yFlow1.YFlowId }
        def yFlow2 = yFlowHelper.addYFlow(yFlowRequest2)
        def yFlowRequest3 = yFlowHelper.singleSwitchYFlow(swT.getShared(), false, [yFlowRequest1, yFlowRequest2])
                .tap { it.diverseFlowId = yFlow1.YFlowId }
        def yFlow3 = yFlowHelper.addYFlow(yFlowRequest3)

        and: "Get flow paths for all y-flows"
        YFlowPaths yFlow1Paths, yFlow2Paths, yFlow3Paths
        withPool {
            (yFlow1Paths, yFlow2Paths, yFlow3Paths) = [yFlow1, yFlow2, yFlow3]
                    .collectParallel { northboundV2.getYFlowPaths(it.YFlowId) }
        }

        then: 'All YFlows have correct diverse group information'
        /* https://github.com/telstra/open-kilda/issues/5191
        def yFlow1ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow1Paths, yFlow2Paths, yFlow3Paths)
        verifySegmentsStats(yFlow1Paths, yFlow1ExpectedValues)

        def yFlow2ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow2Paths, yFlow1Paths, yFlow3Paths)
        verifySegmentsStats(yFlow2Paths, yFlow2ExpectedValues)

        def yFlow3ExpectedValues = expectedThreeYFlowsPathIntersectionValuesMap(yFlow3Paths, yFlow1Paths, yFlow2Paths)
        verifySegmentsStats(yFlow3Paths, yFlow3ExpectedValues)
        */
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow1Paths, [yFlow2, yFlow3])
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow2Paths, [yFlow1, yFlow3])
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow3Paths, [yFlow2, yFlow1])

        cleanup:
        withPool {
            [yFlow1, yFlow2, yFlow3].eachParallel { it && yFlowHelper.deleteYFlow(it.YFlowId) }
        }
    }

    @Tidy
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
        def yFlow1Request = yFlowHelper.randomYFlow(swT, false)
        def yFlow1 = yFlowHelper.addYFlow(yFlow1Request)
        def yFlow2Request = yFlowHelper.randomYFlow(swT, false)
        def yFlow2 = yFlowHelper.addYFlow(yFlow2Request)
        def allSubFlowsIds = yFlow1.subFlows*.flowId + yFlow2.subFlows*.flowId as Set


        when: "Partially update Y-Flow to become diverse with another one"
        yFlowHelper.partialUpdateYFlow(yFlow1.getYFlowId(), YFlowPatchPayload.builder()
                .diverseFlowId(yFlow2.getYFlowId())
                .build())

        and: "Request paths for both Y-Flows"
        YFlowPaths yFlow1Paths, yFlow2Paths
        withPool {
            (yFlow1Paths, yFlow2Paths) = [yFlow1, yFlow2]
                    .collectParallel { northboundV2.getYFlowPaths(it.YFlowId) }
        }

        then: "Path request contain all the subflows in diverse group"
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow1Paths, [yFlow2])
        "assert that sub-flows paths have all expected flow ids in 'other flows' section"(yFlow2Paths, [yFlow1])


        cleanup:
        withPool {
            [yFlow1, yFlow2].eachParallel{
                it && yFlowHelper.deleteYFlow(it.getYFlowId())
            }
        }
    }

    void verifySegmentsStats(YFlowPaths yFlowPaths, Map expectedValuesMap) {
        yFlowPaths.subFlowPaths.each { flow ->
            flow.diverseGroup && with(flow.diverseGroup) { diverseGroup ->
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

        def expectedThreeYFlowsPathIntersectionValuesMap(YFlowPaths yFlowUnderTest,
                                                     YFlowPaths yFlow2Paths,
                                                     YFlowPaths yFlow3Paths) {
        return [
                diverseGroup: [
                        (yFlowUnderTest.subFlowPaths[0].flowId): pathHelper.getOverlappingSegmentStats(
                                yFlowUnderTest.subFlowPaths[0].forward,
                                [yFlow2Paths.subFlowPaths[0].forward,
                                 yFlow2Paths.subFlowPaths[1].forward,
                                 yFlow3Paths.subFlowPaths[0].forward,
                                 yFlow3Paths.subFlowPaths[1].forward]),
                        (yFlowUnderTest.subFlowPaths[1].flowId): pathHelper.getOverlappingSegmentStats(
                                yFlowUnderTest.subFlowPaths[1].forward,
                                [yFlow2Paths.subFlowPaths[0].forward,
                                 yFlow2Paths.subFlowPaths[1].forward,
                                 yFlow3Paths.subFlowPaths[0].forward,
                                 yFlow3Paths.subFlowPaths[1].forward])
                ],
                otherFlows  : [
                        (yFlowUnderTest.subFlowPaths[0].flowId): [
                                (yFlow2Paths.subFlowPaths[0].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[0].forward,
                                        [yFlow2Paths.subFlowPaths[0].forward]),
                                (yFlow2Paths.subFlowPaths[1].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[0].forward,
                                        [yFlow2Paths.subFlowPaths[1].forward]),
                                (yFlow3Paths.subFlowPaths[0].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[0].forward,
                                        [yFlow3Paths.subFlowPaths[0].forward]),
                                (yFlow3Paths.subFlowPaths[1].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[0].forward,
                                        [yFlow3Paths.subFlowPaths[1].forward])
                        ],
                        (yFlowUnderTest.subFlowPaths[1].flowId): [
                                (yFlow2Paths.subFlowPaths[0].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[1].forward,
                                        [yFlow2Paths.subFlowPaths[0].forward]),
                                (yFlow2Paths.subFlowPaths[1].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[1].forward,
                                        [yFlow2Paths.subFlowPaths[1].forward]),
                                (yFlow3Paths.subFlowPaths[0].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[1].forward,
                                        [yFlow3Paths.subFlowPaths[0].forward]),
                                (yFlow3Paths.subFlowPaths[1].flowId)   : pathHelper.getOverlappingSegmentStats(
                                        yFlowUnderTest.subFlowPaths[1].forward,
                                        [yFlow3Paths.subFlowPaths[1].forward])
                        ]
                ]
        ]
    }

    def "assert that sub-flows paths have all expected flow ids in 'other flows' section"(YFlowPaths yFlowPaths,
                                                                                          List<YFlow> diverseYFlows) {
        def expectedSubFlowIdSet = "get sub-flow id's from all Y-Flows"(diverseYFlows)
        assert yFlowPaths.subFlowPaths[0].diverseGroup.otherFlows*.id as Set ==
                expectedSubFlowIdSet
        assert yFlowPaths.subFlowPaths[1].diverseGroup.otherFlows*.id as Set ==
                expectedSubFlowIdSet
        return true
    }

    Set<String> "get sub-flow id's from all Y-Flows"(List<YFlow> yFlows) {
        return yFlows.collect { it.getSubFlows() }
                .flatten()
                .collect { it.getFlowId() } as Set
    }

}
