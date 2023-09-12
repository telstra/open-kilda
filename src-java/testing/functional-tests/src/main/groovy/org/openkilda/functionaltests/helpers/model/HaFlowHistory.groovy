package org.openkilda.functionaltests.helpers.model


import org.openkilda.testing.service.northbound.model.HaFlowActionType
import org.openkilda.testing.service.northbound.model.HaFlowHistoryEntry

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class HaFlowHistory {
    HaFlowHistory(List<HaFlowHistoryEntry> entries) {
        this.entries = entries
    }

    List<HaFlowHistoryEntry> entries;


    private List<HaFlowHistoryEntry> getEntriesByType(HaFlowActionType type) {
        return entries.findAll({ it -> it.action == type.getValue() })
    }

    boolean hasExactlyNEntriesOfType(HaFlowActionType entryType, int expectedAmountOfEntries) {
        return getEntriesByType(entryType).size() == expectedAmountOfEntries
    }
}
