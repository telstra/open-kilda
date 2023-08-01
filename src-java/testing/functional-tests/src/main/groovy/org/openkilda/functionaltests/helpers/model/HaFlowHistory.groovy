package org.openkilda.functionaltests.helpers.model

import org.openkilda.testing.service.northbound.NorthboundServiceV2

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


    List<HaFlowHistoryEntry> getEntryByType(String type) {
        switch (type) {
            case "update": return entries.findAll({ it -> it.action == "HA-Flow update" })
            case "create": return entries.findAll({ it -> it.action == "HA-Flow create" })
            case "delete": return entries.findAll({ it -> it.action == "HA-Flow delete" })
            case "reroute": return entries.findAll({ it -> it.action == "HA-Flow reroute" })
        }

    }


}
