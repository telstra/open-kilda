package org.openkilda.functionaltests.model.stats

enum Origin {
    SERVER_42("server42"),
    FLOW_MONITORING("flow-monitoring"),

    final String value

    Origin(String value) {
        this.value = value
    }
}
