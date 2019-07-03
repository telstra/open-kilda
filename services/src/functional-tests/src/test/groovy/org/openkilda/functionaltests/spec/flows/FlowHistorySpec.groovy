package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that history records are created for the create/update actions.
History record is created in case the create/update action is completed successfully.""")
class FlowHistorySpec extends HealthCheckSpecification {
    String createAction = "Flow creating"
    String createHistoryAction = "Created the flow"
    String updateAction = "Flow updating"
    String updateHistoryAction = "Updated the flow"

    @Shared
    Long timestampBefore

    def setupOnce() {
        timestampBefore = System.currentTimeSeconds() - 5
    }

    @Tags(SMOKE)
    def "History records are created for the create/update actions using custom timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        def flowHistory = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterCreate)
        assert flowHistory.size() == 1
        checkHistoryCreateAction(flowHistory[0], flow.id)

        when: "Update the created flow"
        flowHelper.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeSeconds()
        def flowHistory1 = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterUpdate)
        assert flowHistory1.size() == 2
        checkHistoryUpdateAction(flowHistory1[1], flow.id)

        when: "Delete the updated flow"
        flowHelper.deleteFlow(flow.id)

        then: "History is still available for the deleted flow"
        def flowHistory3 = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterUpdate)
        assert flowHistory3.size() == 2
        checkHistoryDeleteAction(flowHistory3, flow.id)
    }

    def "History records are created for the create/update actions using default timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        def flowHistory = northbound.getFlowHistory(flow.id)
        assert flowHistory.size() == 1
        checkHistoryCreateAction(flowHistory[0], flow.id)

        when: "Update the created flow"
        flowHelper.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        def flowHistory1 = northbound.getFlowHistory(flow.id)
        assert flowHistory1.size() == 2
        checkHistoryUpdateAction(flowHistory1[1], flow.id)

        when: "Delete the updated flow"
        flowHelper.deleteFlow(flow.id)

        then: "History is still available for the deleted flow"
        def flowHistory3 = northbound.getFlowHistory(flow.id)
        assert flowHistory3.size() == 2
        checkHistoryDeleteAction(flowHistory3, flow.id)
    }

    def "History should not be returned in case timeline is incorrect (timeBefore > timeAfter)"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        def flowHistory = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterCreate)
        assert flowHistory.size() == 1
        checkHistoryCreateAction(flowHistory[0], flow.id)

        when: "Try to get history for incorrect timeline"
        def flowH = northbound.getFlowHistory(flow.id, timestampAfterCreate, timestampBefore)

        then: "History record is NOT returned"
        flowH.isEmpty()

        and: "Cleanup: restore default state(remove created flow)"
        flowHelper.deleteFlow(flow.id)
    }

    def "History should not be returned in case flow was never created"() {
        when: "Try to get history for incorrect flowId"
        def flowHistory = northbound.getFlowHistory(NON_EXISTENT_FLOW_ID)

        then: "History record is NOT returned"
        flowHistory.isEmpty()
    }

    void checkHistoryCreateAction(flowHistory, flowId) {
        assert flowHistory.flowId == flowId
        assert flowHistory.action == createAction
        assert flowHistory.taskId
        assert flowHistory.histories.action[0] == createHistoryAction
    }

    void checkHistoryUpdateAction(flowHistory, flowId) {
        assert flowHistory.flowId == flowId
        assert flowHistory.action == updateAction
        assert flowHistory.taskId
        assert flowHistory.histories.action[0] == updateHistoryAction
    }

    /** We pass latest timestamp when changes were done.
     * Just for getting all records from history */
    void checkHistoryDeleteAction(flowHistory, flowId) {
        checkHistoryCreateAction(flowHistory[0], flowId)
        checkHistoryUpdateAction(flowHistory[1], flowId)
    }
}
