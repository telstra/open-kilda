package org.openkilda.functionaltests.helpers.model

import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.northbound.model.HaFlowActionType

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.openkilda.testing.service.northbound.model.HaFlowHistoryEntry

@TupleConstructor
@EqualsAndHashCode
class HaFlowHistory {
    HaFlowHistory(List<HaFlowHistoryEntry> entries) {
        this.entries = entries
    }

    HaFlowHistory(NorthboundServiceV2 northboundServiceV2, List<HaFlowHistoryEntry> entries) {
        this.northboundServiceV2 = northboundServiceV2
        this.entries = entries
    }

    NorthboundServiceV2 northboundServiceV2;
    List<HaFlowHistoryEntry> entries;


    List<HaFlowHistoryEntry> getEntryByType(HaFlowActionType type) {
        switch (type) {
            case HaFlowActionType.UPDATE: return entries.findAll({ it -> it.action == "HA-Flow update" })
            case HaFlowActionType.CREATE: return entries.findAll({ it -> it.action == "HA-Flow create" })
            case HaFlowActionType.DELETE: return entries.findAll({ it -> it.action == "HA-Flow delete" })
            case HaFlowActionType.REROUTE: return entries.findAll({ it -> it.action == "HA-Flow reroute" })
        }

    }


}
