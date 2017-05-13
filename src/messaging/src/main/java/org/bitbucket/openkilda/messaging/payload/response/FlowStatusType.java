package org.bitbucket.openkilda.messaging.payload.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class represents status of flow.
 */
public enum FlowStatusType {
    /**
     * Any flow status is used in requests only.
     */
    ANY("ANY"),

    /**
     * Flow installation status.
     */
    INSTALLATION("INSTALLATION"),

    /**
     * Flow up status.
     */
    UP("UP"),

    /**
     * Flow down status.
     */
    DOWN("DOWN");

    /**
     * Flow status.
     */
    @JsonProperty("status")
    private final String status;

    /**
     * Constructs entity.
     *
     * @param   status  flow status
     */
    @JsonCreator
    FlowStatusType(@JsonProperty("status") final String status) {
        this.status = status;
    }

    /**
     * Returns flow status.
     *
     * @return  flow status
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

