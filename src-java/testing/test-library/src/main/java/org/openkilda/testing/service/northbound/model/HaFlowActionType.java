package org.openkilda.testing.service.northbound.model;

public enum HaFlowActionType {
    CREATE("create"),
    DELETE("delete"),
    UPDATE("update"),
    REROUTE("reroute");

    final String value;

    HaFlowActionType(String value) {
        this.value = value;
    }
}
