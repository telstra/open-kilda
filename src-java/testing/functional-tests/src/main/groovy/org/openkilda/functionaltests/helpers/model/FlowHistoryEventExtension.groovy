package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.payload.history.FlowDumpPayload
import org.openkilda.messaging.payload.history.FlowHistoryEntry
import org.openkilda.messaging.payload.history.FlowHistoryPayload

class FlowHistoryEventExtension {

    String flowId
    Long timestamp
    String timestampIso
    String actor
    String action
    String taskId
    String details
    List<FlowHistoryPayload> payload
    List<FlowDumpPayload> dumps

    FlowHistoryEventExtension(FlowHistoryEntry historyEntry) {
        this.flowId = historyEntry.flowId
        this.timestamp = historyEntry.timestamp
        this.timestampIso = historyEntry.timestampIso
        this.actor = historyEntry.actor
        this.action = historyEntry.action
        this.taskId = historyEntry.taskId
        this.details = historyEntry.details
        this.payload = historyEntry.payload
        this.dumps = historyEntry.dumps
    }
}
