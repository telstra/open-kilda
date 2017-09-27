package org.bitbucket.openkilda.messaging.payload.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class represents state of flow.
 */
public enum FlowState {
    /**
     * Flow allocated state.
     */
    ALLOCATED("Allocated"),

    /**
     * Flow creating/deleting state.
     */
    IN_PROGRESS("In progress"),

    /**
     * Flow up state.
     */
    UP("Up"),

    /**
     * Flow down state.
     */
    DOWN("Down");

    /**
     * Flow state.
     */
    @JsonProperty("state")
    private final String state;

    /**
     * Instance constructor.
     *
     * @param state flow state
     */
    @JsonCreator
    FlowState(@JsonProperty("state") final String state) {
        this.state = state;
    }

    /**
     * Returns flow state.
     *
     * @return flow state
     */
    public String getState() {
        return this.state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return state;
    }
}

