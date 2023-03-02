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

package org.openkilda.messaging.info.network;

import org.openkilda.messaging.payload.flow.PathNodePayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Path implements Serializable {
    @JsonProperty("bandwidth")
    private Long bandwidth;

    @JsonProperty("latency")
    private Duration latency;

    @JsonProperty("nodes")
    private List<PathNodePayload> nodes;

    @JsonProperty("is_backup_path")
    private Boolean isBackupPath;

    @JsonProperty("is_protected_path_available")
    private Boolean isProtectedPathAvailable;

    public Path(@JsonProperty("bandwidth") Long bandwidth,
                @JsonProperty("latency") Duration latency,
                @JsonProperty("nodes") List<PathNodePayload> nodes,
                @JsonProperty("is_backup_path") Boolean isBackupPath,
                @JsonProperty("is_protected_path_available") Boolean isProtectedPathAvailable) {
        this.bandwidth = bandwidth;
        this.latency = latency;
        this.nodes = nodes;
        this.isBackupPath = isBackupPath;
        this.isProtectedPathAvailable = isProtectedPathAvailable;
    }
}
