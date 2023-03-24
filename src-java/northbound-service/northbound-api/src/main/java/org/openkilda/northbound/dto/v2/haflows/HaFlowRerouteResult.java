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

package org.openkilda.northbound.dto.v2.haflows;

import org.openkilda.northbound.dto.v2.flows.FlowPathV2.PathNodeV2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(Include.NON_EMPTY)
public class HaFlowRerouteResult {
    ReroutedSharedPath sharedPath;
    List<ReroutedSubFlowPath> subFlowPaths;
    boolean rerouted;

    @Data
    @Builder
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonInclude(Include.NON_NULL)
    public static class ReroutedSharedPath {
        List<PathNodeV2> nodes;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonInclude(Include.NON_NULL)
    public static class ReroutedSubFlowPath {
        String flowId;
        List<PathNodeV2> nodes;
    }
}
