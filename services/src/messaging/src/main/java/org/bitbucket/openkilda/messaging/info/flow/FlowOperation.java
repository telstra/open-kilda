package org.bitbucket.openkilda.messaging.info.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum FlowOperation {
    CREATE("Create"),
    DELETE("Delete"),
    UPDATE("Update"),
    STATE("State");

    @JsonProperty("operation")
    private final String operation;

    @JsonCreator
    FlowOperation(@JsonProperty("operation") String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return this.operation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return operation;
    }
}

