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

package org.openkilda.messaging.info.switches;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.openkilda.messaging.info.InfoData;

import java.util.List;

@Value
public class SyncRulesResponse extends InfoData {

    @JsonProperty("missing_rules")
    private List<String> missingRules;

    @JsonProperty("proper_rules")
    private List<String> properRules;

    @JsonProperty("excess_rules")
    private List<String> excessRules;

    @JsonProperty("installed_rules")
    private List<String> installedRules;

    @JsonCreator
    public SyncRulesResponse(
            @JsonProperty("missing_rules") List<String> missingRules,
            @JsonProperty("proper_rules") List<String> properRules,
            @JsonProperty("excess_rules") List<String> excessRules,
            @JsonProperty("installed_rules") List<String> installedRules) {
        this.missingRules = missingRules;
        this.properRules = properRules;
        this.excessRules = excessRules;
        this.installedRules = installedRules;
    }
}
