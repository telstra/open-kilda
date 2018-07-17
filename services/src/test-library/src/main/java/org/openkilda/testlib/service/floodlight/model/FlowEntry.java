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

package org.openkilda.testlib.service.floodlight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class FlowEntry implements Serializable {

    private String cookie;
    private String durationSeconds;
    private String durationNanoSeconds;
    private long tableId;
    private long packetCount;
    private String version;
    private int priority;
    private long idleTimeout;
    private long hardTimeout;
    private long byteCount;
    private FlowMatchField match;
    private FlowInstructions instructions;

    public FlowEntry(
            @JsonProperty("cookie") String cookie, @JsonProperty("duration-sec") String durationSeconds,
            @JsonProperty("duration-nsec") String durationNanoSeconds, @JsonProperty("table-id") long tableId,
            @JsonProperty("packet-count") long packetCount, @JsonProperty("version") String version,
            @JsonProperty("priority") int priority, @JsonProperty("idle-timeout") long idleTimeout,
            @JsonProperty("hard-timeout") long hardTimeout, @JsonProperty("byte-count") long byteCount,
            @JsonProperty("match") FlowMatchField match, @JsonProperty("instructions") FlowInstructions instructions) {
        this.cookie = cookie;
        this.durationSeconds = durationSeconds;
        this.durationNanoSeconds = durationNanoSeconds;
        this.tableId = tableId;
        this.packetCount = packetCount;
        this.version = version;
        this.priority = priority;
        this.idleTimeout = idleTimeout;
        this.hardTimeout = hardTimeout;
        this.byteCount = byteCount;
        this.match = match;
        this.instructions = instructions;
    }
}
