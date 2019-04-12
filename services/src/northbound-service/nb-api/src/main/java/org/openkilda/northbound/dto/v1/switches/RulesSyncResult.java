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

package org.openkilda.northbound.dto.v1.switches;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RulesSyncResult extends RulesValidationResult {

    @JsonProperty("installed_rules")
    private List<Long> installedRules;

    @JsonProperty("removed_rules")
    private List<Long> removedRules;

    public RulesSyncResult(
            @JsonProperty("missing_rules") List<Long> missingRules,
            @JsonProperty("proper_rules") List<Long> properRules,
            @JsonProperty("excess_rules") List<Long> excessRules,
            @JsonProperty("installed_rules") List<Long> installedRules,
            @JsonProperty("removed_rules") List<Long> removedRules) {
        super(missingRules, properRules, excessRules);

        this.installedRules = installedRules;
        this.removedRules = removedRules;
    }
}
