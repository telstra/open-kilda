package org.bitbucket.openkilda.messaging.info.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents switch event message types.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum SwitchEventType {
    /**
     * Activated switch event message type.
     */
    ACTIVATED("ACTIVATED"),

    /**
     * Added switch event message type.
     */
    ADDED("ADDED"),

    /**
     * Changed switch event message type.
     */
    CHANGED("CHANGED"),

    /**
     * Deactivated switch event message type.
     */
    DEACTIVATED("DEACTIVATED"),

    /**
     * Removed switch event message type.
     */
    REMOVED("REMOVED");

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
    SwitchEventType(final String type) {
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
