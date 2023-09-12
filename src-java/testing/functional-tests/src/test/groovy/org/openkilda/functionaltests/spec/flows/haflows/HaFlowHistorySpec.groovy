package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.testing.service.northbound.model.HaFlowActionType

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that history records are created for the basic actions applied to Ha-Flow.""")
class HaFlowHistorySpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    boolean isDeleted

    @Tidy
    def "User can change an Ha-Flow and get its history event - #type"() {
        given: "HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "Change the flow"
        change(haFlow)
        if (type == HaFlowActionType.DELETE) {
            isDeleted = true
        }

        then: "Correct event appears in HA-Flow history"
        haFlowHelper.getHistory(haFlow.haFlowId).hasExactlyNEntriesOfType(type, 1)

        cleanup:
        haFlow && !isDeleted && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where:
        type      | change

//    @Ignore("https://github.com/telstra/open-kilda/issues/5320")
//        "update"    |
//                { HaFlow flow ->
//                    def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
//                            flow.sharedEndpoint.switchId)) - flow.sharedEndpoint.portNumber
//                    flow.sharedEndpoint.portNumber = allowedSharedPorts[0]
//                    def updatedHaFlowPayload = haFlowHelper.convertToUpdate(flow)
//                    haFlowHelper.updateHaFlow(flow.haFlowId, updatedHaFlowPayload)
//
//                }            | { HaFlow flow -> haFlowHelper.getHistory(flow.haFlowId).getUpdateEntries().size() == 1}
                HaFlowActionType.DELETE |
                { HaFlow flow ->
                    haFlowHelper.deleteHaFlow(flow.haFlowId)

                }

        HaFlowActionType.REROUTE  |
                { HaFlow flow ->
                    haFlowHelper.rerouteHaFlow(flow.haFlowId)

                }
        HaFlowActionType.CREATE | {}
    }


    @Tidy
    def "History records can be received with timestamp filters"() {
        given: "HA-Flow"
        def timestampBeforeCreate = System.currentTimeSeconds()
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Delete HA-flow"
        def timestampBeforeDelete = System.currentTimeSeconds()
        haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        isDeleted = true

        then: "Possible to get HA-Flow history events with timestamp filters"
        def timestampAfterDelete = System.currentTimeSeconds()
        haFlowHelper.getHistory(haFlow.haFlowId, timestampBeforeCreate, timestampAfterDelete).entries.size() == 2
        haFlowHelper.getHistory(haFlow.haFlowId, timestampBeforeDelete, timestampAfterDelete).hasExactlyNEntriesOfType(HaFlowActionType.DELETE, 1)

        cleanup:
        haFlow && !isDeleted && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tidy
    def "Empty history returned in case filters return no results"() {
        given: "HA-Flow"
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Get timestamp after create event"
        def timestampAfterCreate = System.currentTimeSeconds() + 1

        then: "Check HA-Flow history has no entries"
        assert haFlowHelper.getHistory(haFlow.haFlowId, timestampAfterCreate, System.currentTimeSeconds()).entries.isEmpty()

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }
}
