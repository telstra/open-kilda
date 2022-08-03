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

package org.openkilda.messaging.info.switches.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class RuleInfoEntryV2 implements Serializable {
    Long cookie;
    String cookieHex;
    String cookieKind;
    Integer tableId;
    Integer priority;
    String flowId;
    String flowPath;

    @JsonProperty("y_flow_id")
    private String yFlowId;
    List<String> flags;
    Map<String, FieldMatch> match;
    Instructions instructions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(value = SnakeCaseStrategy.class)
    @Builder
    public static class FieldMatch implements Serializable {
        Long value;
        Long mask;

        @JsonProperty("is_masked")
        boolean isMasked;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(value = SnakeCaseStrategy.class)
    @Builder
    public static class WriteMetadata implements Serializable {
        Long value;
        Long mask;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(value = SnakeCaseStrategy.class)
    @Builder
    public static class Instructions implements Serializable {
        Integer goToTable;
        Long goToMeter;
        WriteMetadata writeMetadata;
        List<String> applyActions;
        List<String> writeActions;
    }
}
