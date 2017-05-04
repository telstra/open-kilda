package org.bitbucket.openkilda.messaging.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents types of command destinations.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum CommandDestinationType {
    /**
     * OpenFlow Speaker service destination.
     */
    CONTROLLER("CONTROLLER"),

    /**
     * Topology-Engine service destination.
     */
    TOPOLOGY_ENGINE("TOPOLOGY_ENGINE"),

    /**
     * Northbound API service destination.
     */
    NORTHBOUND("NORTHBOUND");

    /**
     * Message destination.
     */
    @JsonProperty("destination")
    private final String destination;

    /**
     * Constructs entity.
     *
     * @param destination message destination
     */
    @JsonCreator
    CommandDestinationType(@JsonProperty("destination") final String destination) {
        this.destination = destination;
    }

    /**
     * Returns message destination.
     *
     * @return message destination
     */
    public String getDestination() {
        return this.destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return destination;
    }
}
