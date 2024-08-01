package org.openkilda.functionaltests.helpers.model


import org.openkilda.messaging.payload.history.FlowHistoryEntry

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class FlowHistory {
    List<FlowHistoryEventExtension> entries

    FlowHistory(List<FlowHistoryEntry> entries) {
        this.entries = entries.collect { historyEvent ->
            new FlowHistoryEventExtension(historyEvent)
        }
    }

    List<FlowHistoryEventExtension> getEntriesByType(YFlowActionType type) {
        entries.findAll { it.action == type.getValue() }
    }

    List<FlowHistoryEventExtension> getEntriesByType(FlowActionType type) {
        entries.findAll { it.action == type.getValue() }
    }

    int getEventsNumber() {
        entries.size()
    }
}
