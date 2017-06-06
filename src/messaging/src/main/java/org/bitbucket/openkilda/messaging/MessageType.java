package org.bitbucket.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents types of messages.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum MessageType {
    /**
     * Command message type.
     */
    @JsonProperty("COMMAND")
    COMMAND("COMMAND"),

    /**
     * Information message type.
     */
    @JsonProperty("INFO")
    INFO("INFO"),

    /**
     * Error message type.
     */
    @JsonProperty("ERROR")
    ERROR("ERROR");

    /**
     * Message type.
     */
    @JsonProperty("type")
    private final String type;

    /**
     * Constructs entity.
     *
     * @param type message type
     */
    @JsonCreator
    MessageType(@JsonProperty("type") final String type) {
        this.type = type;
    }

    /**
     * Returns message type.
     *
     * @return message type
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return type;
    }
}
