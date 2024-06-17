package org.openkilda.functionaltests.helpers.model


enum FlowStatusHistoryEvent {
    UP("UP"),
    DELETED("DELETED")

    String status

    FlowStatusHistoryEvent(String status) {
        this.status = status
    }

    static FlowStatusHistoryEvent getByValue(String value) {
        for (FlowStatusHistoryEvent flowStatus : values()) {
            if (flowStatus.status == value) {
                return flowStatus
            }
        }
        throw new IllegalArgumentException("Invalid value for flow status in the history flow status response")
    }
}
