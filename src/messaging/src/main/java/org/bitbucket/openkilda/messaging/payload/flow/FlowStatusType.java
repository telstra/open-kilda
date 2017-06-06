package org.bitbucket.openkilda.messaging.payload.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class represents status of flow.
 */
public enum FlowStatusType {
    /**
     * Flow allocated status.
     */
    ALLOCATED("Allocated"),

    /**
     * Flow creating/deleting status.
     */
    IN_PROGRESS("In progress"),

    /**
     * Flow up status.
     */
    UP("Up"),

    /**
     * Flow down status.
     */
    DOWN("Down");

    /**
     * Flow status.
     */
    @JsonProperty("status")
    private final String status;

    /**
     * Constructs entity.
     *
     * @param status flow status
     */
    @JsonCreator
    FlowStatusType(@JsonProperty("status") final String status) {
        this.status = status;
    }

    /**
     * Returns flow status.
     *
     * @return flow status
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return status;
    }
}

