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
     * Instance constructor.
     *
     * @param type info message type
     */
    @JsonCreator
    PortChangeType(final String type) {
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
     * Gets {@code}PortChangeType{@code} object from string ignoring case.
     *
     * @param state the string representation of port state.
     * @return the {@code}PortChangeType{@code} object.
     */
    public static PortChangeType from(String state) {
        return Arrays.stream(PortChangeType.values())
                .filter(item -> item.type.equalsIgnoreCase(state))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Incorrect port state: %s", state)));
    }

    public boolean isActive() {
        return this == ADD || this == UP;
    }
}
