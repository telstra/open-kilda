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

package org.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serializable;

/**
 * TODO: add javadoc.
 */
@Getter
public class FlowStatsEntry implements Serializable {
    @JsonProperty
    private int tableId;

    @JsonProperty
    private int priority;

    @JsonProperty
    private long cookie;

    @JsonProperty
    private long packetCount;

    @JsonProperty
    private long byteCount;

    @JsonProperty
    private int inPort;

    @JsonProperty
    private int outPort;

    @JsonCreator
    public FlowStatsEntry(@JsonProperty("tableId") int tableId,
                          @JsonProperty("priority") int priority,
                          @JsonProperty("cookie") long cookie,
                          @JsonProperty("packetCount") long packetCount,
                          @JsonProperty("byteCount") long byteCount,
                          @JsonProperty("inPort") int inPort,
                          @JsonProperty("outPort") int outPort) {
        this.tableId = tableId;
        this.priority = priority;
        this.cookie = cookie;
        this.packetCount = packetCount;
        this.byteCount = byteCount;
        this.inPort = inPort;
        this.outPort = outPort;
    }

    public FlowStatsEntry(int tableId, long cookie, long packetCount, long byteCount, int inPort, int outPort) {
        this(tableId, -1, cookie, packetCount, byteCount, inPort, outPort);
    }
}
