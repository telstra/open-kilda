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

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
public class SyncRulesResponse extends InfoData {

    @JsonProperty("missing_rules")
    private List<Long> missingRules;

    @JsonProperty("proper_rules")
    private List<Long> properRules;

    @JsonProperty("excess_rules")
    private List<Long> excessRules;

    @JsonProperty("installed_rules")
    private List<Long> installedRules;

    @JsonCreator
    public SyncRulesResponse(
            @JsonProperty("missing_rules") List<Long> missingRules,
            @JsonProperty("proper_rules") List<Long> properRules,
            @JsonProperty("excess_rules") List<Long> excessRules,
            @JsonProperty("installed_rules") List<Long> installedRules) {
        this.missingRules = missingRules;
        this.properRules = properRules;
        this.excessRules = excessRules;
        this.installedRules = installedRules;
    }

    public SyncRulesResponse() {
        this.missingRules = Collections.emptyList();
        this.properRules = Collections.emptyList();
        this.excessRules = Collections.emptyList();
        this.installedRules = Collections.emptyList();
    }
}
