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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class RulesSyncResult extends RulesValidationResult {

    @JsonProperty("installed_rules")
    private List<Long> installedRules;

    @JsonCreator
    public RulesSyncResult(
            @JsonProperty("missing_rules") List<Long> missingRules,
            @JsonProperty("proper_rules") List<Long> properRules,
            @JsonProperty("excess_rules") List<Long> excessRules,
            @JsonProperty("installed_rules") List<Long> installedRules) {
        super(missingRules, properRules, excessRules);

        this.installedRules = installedRules;
    }
}
