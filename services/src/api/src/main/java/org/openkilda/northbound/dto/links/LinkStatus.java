/* Copyright 2018 Telstra Open Source
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

package org.openkilda.northbound.dto.links;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public enum LinkStatus {
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
    LinkStatus(final String type) {
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return type;
    }

    /**
     * Find corresponding value for string representation of status.
     * @param state ISL state.
     * @return {@link LinkStatus} value.
     */
    public static LinkStatus from(String state) {
        return Arrays.stream(LinkStatus.values())
                .filter(item -> item.type.equalsIgnoreCase(state))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Incorrect isl state: %s", state)));
    }

}
