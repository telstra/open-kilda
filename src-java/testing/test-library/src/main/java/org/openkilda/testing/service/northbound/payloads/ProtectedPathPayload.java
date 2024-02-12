/* Copyright 2023 Telstra Open Source
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

package org.openkilda.testing.service.northbound.payloads;

import org.openkilda.messaging.payload.flow.PathNodePayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.util.List;

@EqualsAndHashCode
@Data
@Setter(value = AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@NoArgsConstructor
public class ProtectedPathPayload {
    Long bandwidth;
    Long latency;
    Long latencyNs;
    Long latencyMs;
    List<PathNodePayload> nodes;
    Boolean isBackupPath;

    public ProtectedPathPayload(Long bandwidth, Duration latency, List<PathNodePayload> nodes, Boolean isBackupPath) {
        this(bandwidth, latency.toNanos(), latency.toNanos(), latency.toMillis(), nodes, isBackupPath);
    }
}
