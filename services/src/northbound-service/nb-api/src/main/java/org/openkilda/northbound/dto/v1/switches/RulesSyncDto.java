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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RulesSyncDto extends RulesValidationDto {

    @JsonProperty("missing")
    private List<Long> missing;

    @JsonProperty("misconfigured")
    private List<Long> misconfigured;

    @JsonProperty("proper")
    private List<Long> proper;

    @JsonProperty("excess")
    private List<Long> excess;

    @JsonProperty("installed")
    private List<Long> installed;

    @JsonProperty("removed")
    private List<Long> removed;
}
