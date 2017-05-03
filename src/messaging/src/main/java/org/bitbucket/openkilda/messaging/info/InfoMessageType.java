package org.bitbucket.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents info message types.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum InfoMessageType {
    /**
     * Path info message type.
     */
    PATH("PATH"),

    /**
     * ISL info message type.
     */
    ISL("ISL"),

    /**
     * Switch info message type.
     */
    SWITCH("SWITCH"),

    /**
     * Port info message type.
     */
    PORT("PORT");

    /**
     * Info Message type.
     */
    @JsonProperty("type")
    private final String type;

    /**
     * Constructs entity.
     *
     * @param type info message type
     */
    @JsonCreator
    InfoMessageType(final String type) {
        this.type = type;
    }

    /**
     * Returns info message type.
     *
     * @return info message type
     */
    public String getType() {
        return this.type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return type;
    }
}
