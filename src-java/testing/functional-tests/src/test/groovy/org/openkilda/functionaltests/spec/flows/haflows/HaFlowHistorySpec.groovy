package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.northbound.dto.v2.haflows.HaFlow

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

    @Shared
    boolean  isDeleted


    @Tidy
    def "User can change a ha-flow and get its history event - #description"() {
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "#description the flow"
        change(haFlow)

        then: "Ha flow update event appears"
        expectedAssertion(haFlow)

        cleanup:
        !isDeleted && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where:
        description | change | expectedAssertion

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
        "delete"    |
                { HaFlow flow ->
                    haFlowHelper.deleteHaFlow(flow.haFlowId)
                    isDeleted = true

                }            | { HaFlow flow -> haFlowHelper.getHistory(flow.haFlowId).getDeleteEntries().size() == 1
                                     }

        "reroute"    |
                { HaFlow flow ->
                    haFlowHelper.rerouteHaFlow(flow.haFlowId)

                }            | { HaFlow flow -> haFlowHelper.getHistory(flow.haFlowId).getRerouteEntries().size() == 1}
        "create"    |
                { HaFlow flow ->
                    haFlowHelper.rerouteHaFlow(flow.haFlowId)

                }            | { HaFlow flow -> haFlowHelper.getHistory(flow.haFlowId).getCreateEntries().size() == 1}
    }


    def "History records can be get with timestamp filters"() {
        given: "Ha flow"
        def timestampBeforeCreate = System.currentTimeSeconds()
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Delete HA flow"
        def timestampBeforeDelete = System.currentTimeSeconds()
        haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        Wrappers.wait(WAIT_OFFSET) { assert !northboundV2.getHaFlow(haFlow.haFlowId) }
        def flowRemoved = true

        then: "Possible to get ha flow history events with timestamp filters"
        def timestampAfterDelete = System.currentTimeSeconds()
        haFlowHelper.getHistory(haFlow.haFlowId, timestampBeforeCreate, timestampAfterDelete).entries.size() == 2
        haFlowHelper.getHistory(haFlow.haFlowId, timestampBeforeDelete, timestampAfterDelete).getDeleteEntries().size() == 1

        cleanup:
        haFlow && !flowRemoved && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

    }

    def "Empty history returned in case filters return no results (dateTo < dateBefore)"() {
        given: "Ha flow"
        def timestampBeforeCreate = System.currentTimeSeconds()
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Delete ha flow"
        def timestampAfterDelete = System.currentTimeSeconds()

        then: "Check ha flow history ahs no entries"
        assert haFlowHelper.getHistory(haFlow.haFlowId, timestampAfterDelete, timestampBeforeCreate).entries.isEmpty()

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

    }


}
