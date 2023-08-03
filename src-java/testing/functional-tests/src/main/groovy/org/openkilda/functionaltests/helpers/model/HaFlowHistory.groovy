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


    private List<HaFlowHistoryEntry> getEntriesByType(HaFlowActionType type) {
        return entries.findAll({ it -> it.action == type.getValue() })
    }

    boolean hasExactlyNEntriesOfType(HaFlowActionType entryType, int expectedAmountOfEntries) {
        return getEntriesByType(entryType).size() == expectedAmountOfEntries
    }

}





