/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.info.switches;

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Arrays;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum LogicalPortType {
    LAG("LAG"),
    BFD("BFD"),
    RESERVED("RESERVED");

    @JsonProperty("type")
    private final String type;

    @JsonCreator
    LogicalPortType(final String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

    public String toString() {
        return type;
    }

    /**
     * Gets {@code}LogicalPortType{@code} object from string ignoring case.
     *
     * @param type the string representation of logical port type.
     * @return the {@code}LogicalPortType{@code} object.
     */
    public static LogicalPortType of(String type) {
        return Arrays.stream(LogicalPortType.values())
                .filter(item -> item.type.equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(format("Incorrect logical port type: %s", type)));
    }
}
