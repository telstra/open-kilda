package org.openkilda.functionaltests.helpers.model

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FlowHistoryStatus {
    Long timestamp
    FlowStatusHistoryEvent statusBecome

    FlowHistoryStatus(String timestampIso, String status) {
        this.timestamp = ZonedDateTime.parse(timestampIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant().toEpochMilli()
        this.statusBecome = FlowStatusHistoryEvent.getByValue(status)
    }
}
