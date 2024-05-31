package org.openkilda.functionaltests.helpers.model

import groovy.transform.Canonical
import groovy.transform.ToString
import org.openkilda.messaging.payload.history.HaSubFlowPayload
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchId
import org.openkilda.model.history.DumpType
import org.openkilda.testing.service.northbound.model.HaFlowActionType
import org.openkilda.testing.service.northbound.model.HaFlowDumpPayload
import org.openkilda.testing.service.northbound.model.HaFlowHistoryEntry
import org.openkilda.testing.service.northbound.model.HaFlowHistoryPayload
import org.openkilda.testing.tools.SoftAssertionsWrapper

import java.time.Instant

@ToString
@Canonical
class HaFlowHistoryEventExtension {
    String haFlowId
    Instant time
    String timestampIso
    String actor
    String action
    String taskId
    String details
    List<HaFlowHistoryPayload> payloads
    List<HaFlowDumpPayload> dumps

    HaFlowHistoryEventExtension(HaFlowHistoryEntry historyEvent) {
        this.haFlowId = historyEvent.haFlowId
        this.timestampIso = historyEvent.timestampIso
        this.time = historyEvent.time
        this.actor = historyEvent.actor
        this.action = historyEvent.action
        this.taskId = historyEvent.taskId
        this.details = historyEvent.details
        this.payloads = historyEvent.payloads
        this.dumps = historyEvent.dumps
    }

    void verifyBasicFields(String flowId, HaFlowActionType actionType) {
        SoftAssertionsWrapper assertions = new SoftAssertionsWrapper()
        assertions.checkSucceeds { assert haFlowId == flowId }
        assertions.checkSucceeds { assert taskId }
        assertions.checkSucceeds { assert timestampIso }
        assertions.checkSucceeds { assert payloads.action.find { it == actionType.getPayloadLastAction() } }
        assertions.checkSucceeds { payloads.every { it.timestampIso } }
        assertions.verify()
    }

    void verifyDumpSection(DumpType dumpType, HaFlowExtended haFlow) {
        SoftAssertionsWrapper assertions = new SoftAssertionsWrapper()
        def dumps = dumps.findAll { it.dumpType == dumpType }
        assert dumps.size() == 1
        assertions.checkSucceeds { assert dumps[0].haFlowId == haFlow.haFlowId }
        assertions.checkSucceeds { assert dumps[0].maximumBandwidth == haFlow.maximumBandwidth }
        assertions.checkSucceeds { assert dumps[0].priority == haFlow.priority }
        assertions.checkSucceeds { assert dumps[0].ignoreBandwidth == haFlow.ignoreBandwidth }
        assertions.checkSucceeds { assert dumps[0].allocateProtectedPath == haFlow.allocateProtectedPath }
        assertions.checkSucceeds { assert dumps[0].encapsulationType.toString() == FlowEncapsulationType.TRANSIT_VLAN.toString() }
        assertions.checkSucceeds { assert dumps[0].maxLatency ? dumps[0].maxLatency == haFlow.maxLatency * 1000000 : true }
        assertions.checkSucceeds { assert dumps[0].periodicPings == haFlow.periodicPings }
        assertions.checkSucceeds { assert dumps[0].sharedSwitchId == haFlow.sharedEndpoint.switchId.toString() }
        assertions.checkSucceeds { assert dumps[0].sharedOuterVlan == haFlow.sharedEndpoint.vlanId }
        assertions.checkSucceeds { assert dumps[0].sharedPort == haFlow.sharedEndpoint.portNumber }
        assertions.checkSucceeds { assert dumps[0].sharedInnerVlan == haFlow.sharedEndpoint.innerVlanId }
        assertions.checkSucceeds { assert getSubFlowsEndpoints(dumps[0].haSubFlows).sort() == haFlow.subFlows.sort() }
        assertions.verify()
    }

    static private List<HaSubFlowExtended> getSubFlowsEndpoints(List<HaSubFlowPayload> dumpSubFlowsDetails) {
        dumpSubFlowsDetails.collect { it ->
            HaSubFlowExtended.builder()
            .haSubFlowId(it.haSubFlowId)
            .status(it.status)
            .endpointSwitchId(new SwitchId(it.endpointSwitchId))
            .endpointPort(it.endpointPort)
            .endpointVlan(it.endpointVlan)
            .endpointInnerVlan(it.endpointInnerVlan)
                    .build()
        }
    }
}
