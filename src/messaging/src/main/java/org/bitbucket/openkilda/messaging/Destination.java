package org.bitbucket.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class contains Kilda-specific Kafka components names for message destination.
 */
public enum Destination {
    /**
     * Topology-Engine component.
     */
    TOPOLOGY_ENGINE("TOPOLOGY_ENGINE"),

    /**
     * Controller component.
     */
    CONTROLLER("CONTROLLER"),

    /**
     * WorkFlow Manager component.
     */
    WFM("WFM");

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
    Destination(final String destination) {
        this.destination = destination;
    }

    /**
     * Returns message destination.
     *
     * @return message destination
     */
    public String getType() {
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
