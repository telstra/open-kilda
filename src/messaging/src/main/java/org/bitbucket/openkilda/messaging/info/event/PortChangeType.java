package org.bitbucket.openkilda.messaging.info.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents port change message types.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum PortChangeType {
    /**
     * Add port change message type.
     */
    ADD("ADD"),

    /**
     * Other-update port change message type.
     */
    OTHER_UPDATE("OTHER_UPDATE"),

    /**
     * Delete port change message type.
     */
    DELETE("DELETE"),

    /**
     * Up port change message type.
     */
    UP("UP"),

    /**
     * Down port change message type.
     */
    DOWN("DOWN");

    /**
     * Info Message type.
     */
    @JsonProperty("type")
    private final String type;

    /**
     * Constructs entity.
     *
     * @param   type  info message type
     */
    @JsonCreator
    PortChangeType(final String type) {
        this.type = type;
    }

    /**
     * Returns info message type.
     *
     * @return  info message type
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
