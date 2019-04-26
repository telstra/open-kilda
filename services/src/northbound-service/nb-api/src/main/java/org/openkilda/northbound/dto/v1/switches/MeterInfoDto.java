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

package org.openkilda.northbound.dto.v1.switches;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeterInfoDto {

    @JsonProperty("meter_id")
    private Long meterId;

    @JsonProperty("cookie")
    private Long cookie;

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("rate")
    private Long rate;

    @JsonProperty("burst_size")
    private Long burstSize;

    @JsonProperty("flags")
    private String[] flags;

    @JsonProperty("actual")
    private MeterMisconfiguredInfoDto actual;

    @JsonProperty("expected")
    private MeterMisconfiguredInfoDto expected;
}
