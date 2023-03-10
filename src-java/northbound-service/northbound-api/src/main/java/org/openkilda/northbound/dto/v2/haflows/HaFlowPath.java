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

import org.openkilda.messaging.payload.flow.DiverseGroupPayload;
import org.openkilda.messaging.payload.flow.PathNodePayload;

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
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(Include.NON_EMPTY)
public class HaFlowPath {
    List<PathNodePayload> forward;
    List<PathNodePayload> reverse;
    DiverseGroupPayload diverseGroup;
    HaFlowProtectedPath protectedPath;

    @Data
    @Builder
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonInclude(Include.NON_EMPTY)
    public static class HaFlowProtectedPath {
        List<PathNodePayload> forward;
        List<PathNodePayload> reverse;
        DiverseGroupPayload diverseGroup;
    }
}
