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

package org.openkilda.northbound.dto.v1.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The response for flow validation request.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowValidationDto {

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("as_expected")
    private Boolean asExpected;

    @JsonProperty("pkt_counts")
    private List<Long> pktCounts;

    @JsonProperty("byte_counts")
    private List<Long> byteCounts;

    private List<PathDiscrepancyDto> discrepancies;

    @JsonProperty("flow_rules_total")
    private Integer flowRulesTotal;

    @JsonProperty("switch_rules_total")
    private Integer switchRulesTotal;

    @JsonProperty("flow_meters_total")
    private Integer flowMetersTotal;

    @JsonProperty("switch_meters_total")
    private Integer switchMetersTotal;
}
