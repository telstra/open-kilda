/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.messaging.payload.flow;

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
    DOWN("Down"),

    /**
     * Flow is cached. It means this flow is read from db and cached.
     */
    CACHED("Cached");

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

    public boolean isActive() {
        return this == UP;
    }

    public boolean isActiveOrCached() {
        return this == UP || this == CACHED;
    }
}

