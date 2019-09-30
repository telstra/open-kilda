package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.messaging.payload.history.FlowEventPayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that history records are created for the create/update actions.
History record is created in case the create/update action is completed successfully.""")
class FlowHistorySpec extends HealthCheckSpecification {
    String createAction = "Flow creating"
    String createHistoryActionV1 = "Created the flow"
    String updateAction = "Flow updating"
    String updateHistoryAction = "Updated the flow"
    String createHistoryActionV2 = "Created successfully"

    @Shared
    Long timestampBefore

    @Autowired
    FlowHelperV2 flowHelperV2

    def setupOnce() {
        timestampBefore = System.currentTimeSeconds() - 5
    }

    @Tags([VIRTUAL]) // can't run it on stage env; mapping(v1->v2) is enabled, so flow is always created via V2
    def "History records are created for the create/update actions using custom timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        def flowHistory = northbound.getFlowHistory(flow.id, timestampBefore, timestampAfterCreate)
        assert flowHistory.size() == 1
        checkHistoryCreateV1Action(flowHistory[0], flow.id)

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

    @Tags(SMOKE)
    def "History records are created for the create/update actions using custom timeline (v2)"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        verifyAll(northbound.getFlowHistory(flow.flowId, timestampBefore, timestampAfterCreate)) { flowH ->
            flowH.size() == 1
            checkHistoryCreateV2Action(flowH[0], flow.flowId)
        }

        when: "Update the created flow"
         def flowInfo = northbound.getFlow(flow.flowId)
        flowHelper.updateFlow(flowInfo.id, flowInfo.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeSeconds()
        verifyAll(northbound.getFlowHistory(flow.flowId, timestampBefore, timestampAfterUpdate)){ flowH ->
            flowH.size() == 2
            checkHistoryUpdateAction(flowH[1], flow.flowId)
        }

        when: "Delete the updated flow"
        flowHelper.deleteFlow(flow.flowId)

        then: "History is still available for the deleted flow"
        northbound.getFlowHistory(flow.flowId, timestampBefore, timestampAfterUpdate).size() == 2
    }

    def "History records are created for the create/update actions using default timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "History record is created"
        def flowHistory = northbound.getFlowHistory(flow.id)
        assert flowHistory.size() == 1
        checkHistoryCreateV1Action(flowHistory[0], flow.id)

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
        checkHistoryCreateV1Action(flowHistory[0], flow.id)

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

    void checkHistoryCreateV1Action(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.action == createAction
        assert flowHistory.histories.action[-1] == createHistoryActionV1
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryCreateV2Action(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.action == createAction
        assert flowHistory.histories.action[-1] == createHistoryActionV2
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryUpdateAction(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.action == updateAction
        assert flowHistory.histories.action[-1] == updateHistoryAction
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryCommonStuff(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.flowId == flowId
        assert flowHistory.taskId
    }

    /** We pass latest timestamp when changes were done.
     * Just for getting all records from history */
    void checkHistoryDeleteAction(List<FlowEventPayload> flowHistory, String flowId) {
        checkHistoryCreateV1Action(flowHistory[0], flowId)
        checkHistoryUpdateAction(flowHistory[1], flowId)
    }
}
