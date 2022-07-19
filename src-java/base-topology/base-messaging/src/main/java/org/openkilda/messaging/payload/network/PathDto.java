/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.payload.network;

import org.openkilda.messaging.payload.flow.PathNodePayload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Duration;
import java.util.List;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathDto {
    @JsonProperty("bandwidth")
    private Long bandwidth;

    @JsonProperty("latency")
    private Long latency;

    @JsonProperty("latency_ns")
    private Long latencyNs;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    @JsonProperty("nodes")
    private List<PathNodePayload> nodes;

    @JsonProperty("is_backup_path")
    private Boolean isBackupPath;

    public PathDto(Long bandwidth, Duration latency, List<PathNodePayload> nodes, Boolean isBackupPath) {
        this(bandwidth, latency.toNanos(), latency.toNanos(), latency.toMillis(), nodes, isBackupPath);
    }

    @JsonCreator
    public PathDto(@JsonProperty("bandwidth") Long bandwidth,
                   @JsonProperty("latency") Long latency,
                   @JsonProperty("latency_ns") Long latencyNs,
                   @JsonProperty("latency_ms") Long latencyMs,
                   @JsonProperty("nodes") List<PathNodePayload> nodes,
                   @JsonProperty("is_backup_path") Boolean isBackupPath) {
        this.bandwidth = bandwidth;
        this.latency = latency;
        this.latencyNs = latencyNs;
        this.latencyMs = latencyMs;
        this.nodes = nodes;
        this.isBackupPath = isBackupPath;
    }
}
