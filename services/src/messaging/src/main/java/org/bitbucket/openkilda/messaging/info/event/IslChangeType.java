package org.bitbucket.openkilda.messaging.info.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents isl change message types.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum IslChangeType {
    /**
     * Isl discovered message type.
     */
    DISCOVERED("DISCOVERED"),

    /**
     * Isl discovery failed message type.
     */
    FAILED("FAILED"),

    /**
     * Other-update isl change message type.
     */
    OTHER_UPDATE("OTHER_UPDATE");

    /**
     * Info Message type.
     */
    @JsonProperty("type")
    private final String type;

    /**
     * Instance constructor.
     *
     * @param type info message type
     */
    @JsonCreator
    IslChangeType(final String type) {
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
