package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.messaging.payload.history.FlowHistoryEntry

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that history records are created for the create/update actions.
History record is created in case the create/update action is completed successfully.""")
@Slf4j
class HaFlowHistorySpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    HaFlowHelper haFlowHelper


    @Tidy
    def "History records are created for the create/"() {
        given: "Ha flow"
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))
        def haFlowHistory1 =  northboundV2.getHaFlowHistory(haFlow.haFlowId)
        println(haFlowHistory1)
        when: "Create a flow"

        then: "Possible to get Ha flow history"
        //implement method
        haFlowHelper.getHistory(haFlow.haFlowId).getCreateEntries().size() == 1


        def haFlowHistory =  northboundV2.getHaFlowHistory( haFlow.haFlowId)
        print("todebug   "  + haFlowHistory.toString())



        cleanup:
        haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }





    /** We pass latest timestamp when changes were done.
     * Just for getting all records from history */
    void checkHistoryDeleteAction(List<FlowHistoryEntry> flowHistory, String flowId) {
        checkHistoryCreateAction(flowHistory[0], flowId)
        checkHistoryUpdateAction(flowHistory[1], flowId)
    }
}
