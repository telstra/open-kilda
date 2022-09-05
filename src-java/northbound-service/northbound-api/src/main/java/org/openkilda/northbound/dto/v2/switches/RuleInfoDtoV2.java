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

package org.openkilda.northbound.dto.v2.switches;

import org.openkilda.northbound.dto.HexView;
import org.openkilda.northbound.dto.v2.action.BaseAction;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleInfoDtoV2 implements HexView {
    private Long cookie;
    private String cookieKind;
    private Integer tableId;
    private Integer priority;
    private String flowId;
    private String flowPathId;
    private List<String> flags;
    private Map<String, FieldMatch> match;
    private Instructions instructions;

    @JsonProperty("y_flow_id")
    private String yFlowId;

    @JsonGetter("cookie_hex")
    String getCookieHex() {
        return toHex(cookie);
    }

    @Data
    @AllArgsConstructor
    @JsonNaming(value = SnakeCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldMatch {
        Long value;
        Long mask;
    }

    @Data
    @AllArgsConstructor
    @JsonNaming(value = SnakeCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WriteMetadata {
        Long value;
        Long mask;
    }

    @Data
    @AllArgsConstructor
    @JsonNaming(value = SnakeCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Instructions {
        Integer goToTable;
        Long goToMeter;
        WriteMetadata writeMetadata;
        List<BaseAction> applyActions;
        List<BaseAction> writeActions;
    }
}
