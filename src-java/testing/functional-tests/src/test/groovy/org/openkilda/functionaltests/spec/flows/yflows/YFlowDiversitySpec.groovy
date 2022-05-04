package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.UPDATE_ACTION

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify the ability to create diverse yflows in the system.")
class YFlowDiversitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "Able to create diverse yFlows"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three flows with diversity enabled"
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

        and: "All flows have diverse yFlow IDs in response"
        northboundV2.getYFlow(yFlow1.YFlowId).diverseWithYFlows.sort() == [yFlow2.YFlowId, yFlow3.YFlowId].sort()
        northboundV2.getYFlow(yFlow2.YFlowId).diverseWithYFlows.sort() == [yFlow1.YFlowId, yFlow3.YFlowId].sort()
        northboundV2.getYFlow(yFlow3.YFlowId).diverseWithYFlows.sort() == [yFlow1.YFlowId, yFlow2.YFlowId].sort()


        and: "All yFlows have different paths"
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

        when: "Delete flows"
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
    def "Able to update yFlow to became diverse with simple multiSwitch flow"() {
        given: "Switches with two not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    it.pathsEp1.collect { pathHelper.getInvolvedIsls(it) }
                            .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        and: "YFlow created"
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        and: "Simple multiSwitch flow on the same path as first subFlow"
        def flow = flowHelperV2.randomFlow(swT.shared, swT.ep1, false)
        flowHelperV2.addFlow(flow)
        def subFlow = yFlow.subFlows.first()
        def involvedIslSubFlow = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(subFlow.flowId)))
        def involvedIslSimpleFlow = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow.flowId)))
        assert involvedIslSubFlow == involvedIslSimpleFlow

        when: "Update yflow to become diverse with simple multiSwitch flow"
        def update = yFlowHelper.convertToUpdate(yFlow.tap { it.diverseWithFlows = [flow.flowId] })
        def updateResponse = yFlowHelper.updateYFlow(yFlow.YFlowId, update)

        then: "Update response contains information about diverse flow"
        updateResponse.diverseWithFlows.sort() == [flow.flowId].sort()

        and: "Yflow is really updated"
        with(northboundV2.getYFlow(yFlow.YFlowId)) {
            it.diverseWithFlows.sort() == [flow.flowId].sort()
            it.diverseWithYFlows.empty
        }

        and: "Simple multi switch flow has the 'diverse_with' field"
        with(northboundV2.getFlow(flow.flowId)) {
            it.diverseWithYFlows.sort() == [yFlow.YFlowId].sort()
            it.diverseWith.empty
        }

        and: "First sub flow became diverse and changed path"
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

        when: "Partially update yflow to become not diverse with simple multiSwitch flow"
        def patch =  YFlowPatchPayload.builder().diverseFlowId("").build()
        def patchResponse = yFlowHelper.partialUpdateYFlow(yFlow.YFlowId, patch)

        then: "Update response contains information about diverse flow"
        patchResponse.diverseWithFlows.empty

        and: "Yflow is really updated"
        northboundV2.getYFlow(yFlow.YFlowId).diverseWithFlows.empty

        and: "Simple multi switch flow doesn't have the 'diverse_with' field"
        northboundV2.getFlow(flow.flowId).diverseWithYFlows.empty

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }
}
