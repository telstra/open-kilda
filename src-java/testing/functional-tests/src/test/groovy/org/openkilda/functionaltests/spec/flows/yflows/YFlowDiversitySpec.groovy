package org.openkilda.functionaltests.spec.flows.yflows


import static org.junit.jupiter.api.Assumptions.assumeTrue
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

        then: "Get flow path for all y-flows"
        def yFlow1Paths = northboundV2.getYFlowPaths(yFlow1.YFlowId)
        def yFlow2Paths = northboundV2.getYFlowPaths(yFlow2.YFlowId)
        def yFlow3Paths = northboundV2.getYFlowPaths(yFlow3.YFlowId)

        def yFlow1SwitchCount = pathHelper.getInvolvedSwitches(PathHelper.convert(yFlow1Paths.subFlowPaths[0].forward)).size()
        def yFlow2SwitchCount = pathHelper.getInvolvedSwitches(PathHelper.convert(yFlow2Paths.subFlowPaths[0].forward)).size()
        def yFlow3SwitchCount = pathHelper.getInvolvedSwitches(PathHelper.convert(yFlow3Paths.subFlowPaths[0].forward)).size()
        def yFlow1n2OverlapSwitchCount = Math.min(yFlow1SwitchCount, yFlow2SwitchCount)
        def yFlow1n3OverlapSwitchCount = Math.min(yFlow1SwitchCount, yFlow3SwitchCount)
        def yFlow2n3OverlapSwitchCount = Math.min(yFlow2SwitchCount, yFlow3SwitchCount)

        def yFlow1SharedIsls = pathHelper.getInvolvedIsls(PathHelper.convert(yFlow1Paths.sharedPath.forward)).size()
        def yFlow2SharedIsls = pathHelper.getInvolvedIsls(PathHelper.convert(yFlow2Paths.sharedPath.forward)).size()
        def yFlow3SharedIsls = pathHelper.getInvolvedIsls(PathHelper.convert(yFlow3Paths.sharedPath.forward)).size()

        and: "All y-flow paths have diverse group information"

        def yFlow1n2OverlapSwPercentOf1 = (100 * yFlow1n2OverlapSwitchCount / yFlow1SwitchCount).toInteger()
        def yFlow1n3OverlapSwPercentOf1 = (100 * yFlow1n3OverlapSwitchCount / yFlow1SwitchCount).toInteger()
        def yFlow1ExpectedValues = [
                diverseGroup: [
                        (yFlow1.subFlows[0].flowId): [islCount: yFlow1SharedIsls, switchCount: yFlow1SwitchCount, islPercent: 100, switchPercent: 100],
                        (yFlow1.subFlows[1].flowId): [islCount: yFlow1SharedIsls, switchCount: yFlow1SwitchCount, islPercent: 100, switchPercent: 100]
                ],
                otherFlows  : [
                        (yFlow1.subFlows[0].flowId): [
                                (yFlow1.subFlows[1].flowId): [islCount: yFlow1SharedIsls, switchCount: yFlow1SwitchCount, islPercent: 100, switchPercent: 100],
                                (yFlow2.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf1],
                                (yFlow2.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf1],
                                (yFlow3.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf1],
                                (yFlow3.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf1]
                        ],
                        (yFlow1.subFlows[1].flowId): [
                                (yFlow1.subFlows[0].flowId): [islCount: yFlow1SharedIsls, switchCount: yFlow1SwitchCount, islPercent: 100, switchPercent: 100],
                                (yFlow2.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf1],
                                (yFlow2.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf1],
                                (yFlow3.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf1],
                                (yFlow3.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf1]
                        ]
                ]
        ]
        verifySegmentsStats(yFlow1Paths, yFlow1ExpectedValues)

        def yFlow1n2OverlapSwPercentOf2 = (100 * yFlow1n2OverlapSwitchCount / yFlow2SwitchCount).toInteger()
        def yFlow2n3OverlapSwPercentOf2 = (100 * yFlow2n3OverlapSwitchCount / yFlow2SwitchCount).toInteger()
        def yFlow2ExpectedValues = [
                diverseGroup: [
                        (yFlow2.subFlows[0].flowId): [islCount: yFlow2SharedIsls, switchCount: yFlow2SwitchCount, islPercent: 100, switchPercent: 100],
                        (yFlow2.subFlows[1].flowId): [islCount: yFlow2SharedIsls, switchCount: yFlow2SwitchCount, islPercent: 100, switchPercent: 100]
                ],
                otherFlows  : [
                        (yFlow2.subFlows[0].flowId): [
                                (yFlow1.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf2],
                                (yFlow1.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf2],
                                (yFlow2.subFlows[1].flowId): [islCount: yFlow2SharedIsls, switchCount: yFlow2SwitchCount, islPercent: 100, switchPercent: 100],
                                (yFlow3.subFlows[0].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf2],
                                (yFlow3.subFlows[1].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf2]
                        ],
                        (yFlow2.subFlows[1].flowId): [
                                (yFlow1.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf2],
                                (yFlow1.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n2OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n2OverlapSwPercentOf2],
                                (yFlow2.subFlows[0].flowId): [islCount: yFlow2SharedIsls, switchCount: yFlow2SwitchCount, islPercent: 100, switchPercent: 100],
                                (yFlow3.subFlows[0].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf2],
                                (yFlow3.subFlows[1].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf2]
                        ]
                ]
        ]
        verifySegmentsStats(yFlow2Paths, yFlow2ExpectedValues)

        def yFlow1n3OverlapSwPercentOf3 = (100 * yFlow1n3OverlapSwitchCount / yFlow3SwitchCount).toInteger()
        def yFlow2n3OverlapSwPercentOf3 = (100 * yFlow2n3OverlapSwitchCount / yFlow3SwitchCount).toInteger()
        def yFlow3ExpectedValues = [
                diverseGroup: [
                        (yFlow3.subFlows[0].flowId): [islCount: yFlow3SharedIsls, switchCount: yFlow3SwitchCount, islPercent: 100, switchPercent: 100],
                        (yFlow3.subFlows[1].flowId): [islCount: yFlow3SharedIsls, switchCount: yFlow3SwitchCount, islPercent: 100, switchPercent: 100],
                ],
                otherFlows  : [
                        (yFlow3.subFlows[0].flowId): [
                                (yFlow1.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf3],
                                (yFlow1.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf3],
                                (yFlow2.subFlows[0].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf3],
                                (yFlow2.subFlows[1].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf3],
                                (yFlow3.subFlows[1].flowId): [islCount: yFlow3SharedIsls, switchCount: yFlow3SwitchCount, islPercent: 100, switchPercent: 100]
                        ],
                        (yFlow3.subFlows[1].flowId): [
                                (yFlow1.subFlows[0].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf3],
                                (yFlow1.subFlows[1].flowId): [islCount: 0, switchCount: yFlow1n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow1n3OverlapSwPercentOf3],
                                (yFlow2.subFlows[0].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf3],
                                (yFlow2.subFlows[1].flowId): [islCount: 0, switchCount: yFlow2n3OverlapSwitchCount, islPercent: 0, switchPercent: yFlow2n3OverlapSwPercentOf3],
                                (yFlow3.subFlows[0].flowId): [islCount: yFlow3SharedIsls, switchCount: yFlow3SwitchCount, islPercent: 100, switchPercent: 100]
                        ]
                ]
        ]
        verifySegmentsStats(yFlow3Paths, yFlow3ExpectedValues)

        then: "Get flow path for sub-flows"
        def yFlow1s1Path = northbound.getFlowPath(yFlow1.subFlows[0].flowId)
        def yFlow1s2Path = northbound.getFlowPath(yFlow1.subFlows[1].flowId)
        verifySegmentsStats([yFlow1s1Path, yFlow1s2Path], yFlow1ExpectedValues)

        def yFlow2s1Path = northbound.getFlowPath(yFlow2.subFlows[0].flowId)
        def yFlow2s2Path = northbound.getFlowPath(yFlow2.subFlows[1].flowId)
        verifySegmentsStats([yFlow2s1Path, yFlow2s2Path], yFlow2ExpectedValues)

        def yFlow3s1Path = northbound.getFlowPath(yFlow3.subFlows[0].flowId)
        def yFlow3s2Path = northbound.getFlowPath(yFlow3.subFlows[1].flowId)
        verifySegmentsStats([yFlow3s1Path, yFlow3s2Path], yFlow3ExpectedValues)

        cleanup:
        [yFlow1, yFlow2, yFlow3].each { it && yFlowHelper.deleteYFlow(it.YFlowId) }
    }


    void verifySegmentsStats(YFlowPaths yFlowPaths, Map expectedValuesMap) {
        yFlowPaths.subFlowPaths.each { flow ->
            flow.diverseGroup && with(flow.diverseGroup) { diverseGroup ->
                verifyAll(diverseGroup.overlappingSegments) {
                    islCount == expectedValuesMap["diverseGroup"][flow.flowId]["islCount"]
                    switchCount == expectedValuesMap["diverseGroup"][flow.flowId]["switchCount"]
                    islPercent == expectedValuesMap["diverseGroup"][flow.flowId]["islPercent"]
                    switchPercent == expectedValuesMap["diverseGroup"][flow.flowId]["switchPercent"]
                }
                with(diverseGroup.otherFlows) { otherFlows ->
                    otherFlows.each { otherFlow ->
                        verifyAll(otherFlow.segmentsStats) {
                            islCount == expectedValuesMap["otherFlows"][flow.flowId][otherFlow.id]["islCount"]
                            switchCount == expectedValuesMap["otherFlows"][flow.flowId][otherFlow.id]["switchCount"]
                            islPercent == expectedValuesMap["otherFlows"][flow.flowId][otherFlow.id]["islPercent"]
                            switchPercent == expectedValuesMap["otherFlows"][flow.flowId][otherFlow.id]["switchPercent"]
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
                    islCount == expectedValuesMap["diverseGroup"][flow.id]["islCount"]
                    switchCount == expectedValuesMap["diverseGroup"][flow.id]["switchCount"]
                    islPercent == expectedValuesMap["diverseGroup"][flow.id]["islPercent"]
                    switchPercent == expectedValuesMap["diverseGroup"][flow.id]["switchPercent"]
                }
                with(diverseGroup.otherFlows) { otherFlows ->
                    otherFlows.each { otherFlow ->
                        verifyAll(otherFlow.segmentsStats) {
                            islCount == expectedValuesMap["otherFlows"][flow.id][otherFlow.id]["islCount"]
                            switchCount == expectedValuesMap["otherFlows"][flow.id][otherFlow.id]["switchCount"]
                            islPercent == expectedValuesMap["otherFlows"][flow.id][otherFlow.id]["islPercent"]
                            switchPercent == expectedValuesMap["otherFlows"][flow.id][otherFlow.id]["switchPercent"]
                        }
                    }
                }
            }
        }
    }

}
