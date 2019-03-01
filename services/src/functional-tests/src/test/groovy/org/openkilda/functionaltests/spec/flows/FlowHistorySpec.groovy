package org.openkilda.functionaltests.spec.flows

import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Narrative

@Narrative("""Verify that history records are created for the create/update actions.
History record is created in case the create/update action is completed successfully.""")
class FlowHistorySpec extends BaseSpecification {
    String createAction = "Flow creating"
    String createHistoryAction = "Created the flow"
    String updateAction = "Flow updating"
    String updateHistoryAction = "Updated the flow"

    def "History records are created for the create/update actions using custom timeline"() {
        given: "Current timestamp"
        Long timestampBefore = (System.currentTimeMillis() / 1000L) - 1000

        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeMillis() / 1000L
        def flowH1 = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterCreate)
        flowH1.size() == 1
        flowH1[0].flowId == flow.id
        flowH1[0].action == createAction
        flowH1[0].taskId
        flowH1[0].histories.action[0] == createHistoryAction

        when: "Update the created flow"
        flowHelper.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeMillis() / 1000L
        def flowH2 = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterUpdate)
        flowH2.size() == 2
        flowH2[1].flowId == flow.id
        flowH2[1].action == updateAction
        flowH2[1].taskId
        flowH2[1].histories.action[0] == updateHistoryAction

        when: "Delete the updated flow"
        flowHelper.deleteFlow(flow.id)

        then: "History is still available for the deleted flow"
        def flowH3 = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterUpdate)
        flowH3.size() == 2
        flowH3[0].flowId == flow.id
        flowH3[0].action == createAction
        flowH3[0].taskId
        flowH3[0].histories.action[0] == createHistoryAction

        flowH3[1].flowId == flow.id
        flowH3[1].action == updateAction
        flowH3[1].taskId
        flowH3[1].histories.action[0] == updateHistoryAction
    }

    def "History records are created for the create/update actions using default timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        def flowH1 = northbound.getFlowHistory(flow.id)

        flowH1.size() == 1
        flowH1[0].flowId == flow.id
        flowH1[0].action == createAction
        flowH1[0].taskId
        flowH1[0].histories.action[0] == createHistoryAction

        when: "Update the created flow"
        flowHelper.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        def flowH2 = northbound.getFlowHistory(flow.id)
        flowH2.size() == 2
        flowH2[1].flowId == flow.id
        flowH2[1].action == updateAction
        flowH2[1].taskId
        flowH2[1].histories.action[0] == updateHistoryAction

        when: "Delete the updated flow"
        flowHelper.deleteFlow(flow.id)

        then: "History is still available for the deleted flow"
        def flowH3 = northbound.getFlowHistory(flow.id)
        flowH3.size() == 2
        flowH3[0].flowId == flow.id
        flowH3[0].action == createAction
        flowH3[0].taskId
        flowH3[0].histories.action[0] == createHistoryAction

        flowH3[1].flowId == flow.id
        flowH3[1].action == updateAction
        flowH3[1].taskId
        flowH3[1].histories.action[0] == updateHistoryAction
    }

    def "History should not be returned in case timeline is incorrect (timeBefore > timeAfter)"() {
        given: "Current timestamp"
        Long timestampBefore = (System.currentTimeMillis() / 1000L) - 1000

        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeMillis() / 1000L
        def flowH1 = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterCreate)

        flowH1.size() == 1
        flowH1[0].flowId == flow.id
        flowH1[0].action == createAction
        flowH1[0].taskId
        flowH1[0].histories.action[0] == createHistoryAction

        when: "Try to get history for incorrect timeline"
        def flowH2 = northbound.getFlowHistory(flow.id, timestampAfterCreate, timestampBefore)

        then: "History record is NOT returned"
        flowH2.isEmpty()

        and: "cleanup: Restore default state(remove created flow)"
        flowHelper.deleteFlow(flow.id)
    }

    def "History should not be returned in case flowId was never created"() {
        when: "Try to get history for incorrect flowId"
        def flowHistory = northbound.getFlowHistory(NON_EXISTENT_FLOW_ID)

        then: "History record is NOT returned"
        flowHistory.isEmpty()
    }
}
