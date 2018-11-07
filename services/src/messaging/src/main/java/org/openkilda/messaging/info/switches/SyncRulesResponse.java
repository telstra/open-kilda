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

    @JsonProperty("missing_meters")
    private List<Long> missingMeters;

    @JsonProperty("misconfigured_meters")
    private List<Long> misconfiguredMeters;

    @JsonProperty("proper_meters")
    private List<Long> properMeters;

    @JsonProperty("excess_meters")
    private List<Long> excessMeters;

    @JsonCreator
    public SyncRulesResponse(
            @JsonProperty("missing_rules") List<Long> missingRules,
            @JsonProperty("proper_rules") List<Long> properRules,
            @JsonProperty("excess_rules") List<Long> excessRules,
            @JsonProperty("installed_rules") List<Long> installedRules,
            @JsonProperty("missing_meters") List<Long> missingMeters,
            @JsonProperty("misconfigured_meters") List<Long> misconfiguredMeters,
            @JsonProperty("proper_meters") List<Long> properMeters,
            @JsonProperty("excess_meters") List<Long> excessMeters) {
        this.missingRules = missingRules;
        this.properRules = properRules;
        this.excessRules = excessRules;
        this.installedRules = installedRules;
        this.missingMeters = missingMeters;
        this.misconfiguredMeters = misconfiguredMeters;
        this.excessMeters = excessMeters;
        this.properMeters = properMeters;
    }

    public SyncRulesResponse() {
        this.missingRules = Collections.emptyList();
        this.properRules = Collections.emptyList();
        this.excessRules = Collections.emptyList();
        this.installedRules = Collections.emptyList();
        this.missingMeters = Collections.emptyList();
        this.misconfiguredMeters = Collections.emptyList();
        this.excessMeters = Collections.emptyList();
        this.properMeters = Collections.emptyList();
    }
}
