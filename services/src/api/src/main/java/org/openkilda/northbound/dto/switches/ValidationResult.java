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

package org.openkilda.northbound.dto.switches;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ValidationResult {

    @JsonProperty("rules")
    private Diff rules;

    @JsonProperty("meters")
    private Diff meters;

    @Data
    @AllArgsConstructor
    public static class Diff {
        @JsonProperty("missing")
        private List<Long> missing;

        @JsonProperty("misconfigured")
        private List<Long> misconfigured;

        @JsonProperty("excess")
        private List<Long> excess;

        @JsonProperty("proper")
        private List<Long> proper;
    }
}
