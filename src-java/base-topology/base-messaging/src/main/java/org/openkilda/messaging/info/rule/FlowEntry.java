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

package org.openkilda.messaging.info.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
@EqualsAndHashCode(exclude = {"durationSeconds", "durationNanoSeconds", "packetCount", "byteCount",
        "idleTimeout", "hardTimeout", "version"})
public class FlowEntry implements Serializable {

    @JsonProperty("cookie")
    private long cookie;
    @JsonProperty("duration-sec")
    private long durationSeconds;
    @JsonProperty("duration-nsec")
    private long durationNanoSeconds;
    @JsonProperty("table-id")
    private long tableId;
    @JsonProperty("packet-count")
    private long packetCount;
    @JsonProperty("version")
    private String version;
    @JsonProperty("priority")
    private int priority;
    @JsonProperty("idle-timeout")
    private long idleTimeout;
    @JsonProperty("hard-timeout")
    private long hardTimeout;
    @JsonProperty("byte-count")
    private long byteCount;
    @JsonProperty("match")
    private FlowMatchField match;
    @JsonProperty("instructions")
    private FlowInstructions instructions;
    @JsonProperty("flags")
    private String[] flags;

    @JsonCreator
    public FlowEntry(
            @JsonProperty("cookie") long cookie, @JsonProperty("duration-sec") long durationSeconds,
            @JsonProperty("duration-nsec") long durationNanoSeconds, @JsonProperty("table-id") long tableId,
            @JsonProperty("packet-count") long packetCount, @JsonProperty("version") String version,
            @JsonProperty("priority") int priority, @JsonProperty("idle-timeout") long idleTimeout,
            @JsonProperty("hard-timeout") long hardTimeout, @JsonProperty("byte-count") long byteCount,
            @JsonProperty("match") FlowMatchField match, @JsonProperty("instructions") FlowInstructions instructions,
            @JsonProperty("flags") String[] flags) {
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
        this.flags = flags;
    }
}
