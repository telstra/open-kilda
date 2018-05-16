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
     * Isl was moved (currently it is inactive, new ISL has been replaced this one).
     */
    MOVED("MOVED"),

    /**
     * Isl was created via pre-population.
     */
    CACHED("CACHED"),

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

    public static IslChangeType from(String state) {
        return Arrays.stream(IslChangeType.values())
                .filter(item -> item.type.equalsIgnoreCase(state))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Incorrect isl state: %s", state)));
    }

}
