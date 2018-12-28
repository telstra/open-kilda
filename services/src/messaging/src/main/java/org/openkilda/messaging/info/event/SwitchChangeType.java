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

package org.openkilda.messaging.info.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Arrays;

/**
 * Enum represents switch event message types.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum SwitchChangeType {
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
     * When switch is under validating process.
     */
    VALIDATING("VALIDATING"),

    /**
     * Removed switch event message type.
     */
    REMOVED("REMOVED"),

    /**
     * Switch was cached from via pre-population.
     */
    CACHED("CACHED");

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
    SwitchChangeType(final String type) {
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

    /**
     * Converts a switch state string representation to the instance of {@link SwitchChangeType}.
     *
     * @param state the switch state string representation.
     * @return the instance of {@link SwitchChangeType}.
     * @throws IllegalArgumentException if the incorrect switch state string representation is passed.
     */
    public static SwitchChangeType from(String state) {
        return Arrays.stream(SwitchChangeType.values())
                .filter(item -> item.type.equalsIgnoreCase(state))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(String.format("Incorrect switch state: %s", state)));
    }

    public boolean isActive() {
        return this == ADDED || this == ACTIVATED;
    }
}
