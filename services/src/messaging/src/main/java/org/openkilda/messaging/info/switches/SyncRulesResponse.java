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

import java.util.List;

@Value
public class SyncRulesResponse extends InfoData {

    @JsonProperty("missing")
    private List<Long> missingRules;

    @JsonProperty("proper")
    private List<Long> properRules;

    @JsonProperty("excess")
    private List<Long> excessRules;

    @JsonProperty("installed")
    private List<Long> installedRules;

    @JsonProperty("removed")
    private List<Long> removedRules;

    @JsonCreator
    public SyncRulesResponse(
            @JsonProperty("missing") List<Long> missingRules,
            @JsonProperty("proper") List<Long> properRules,
            @JsonProperty("excess") List<Long> excessRules,
            @JsonProperty("installed") List<Long> installedRules,
            @JsonProperty("removed") List<Long> removedRules) {
        this.missingRules = missingRules;
        this.properRules = properRules;
        this.excessRules = excessRules;
        this.installedRules = installedRules;
        this.removedRules = removedRules;
    }
}
