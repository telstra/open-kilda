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

package org.openkilda.messaging.info.switches;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SwitchValidationResponse extends InfoData {

    @JsonProperty("rules")
    private RulesValidationEntry rules;

    @JsonProperty("meters")
    private MetersValidationEntry meters;

    @JsonCreator
    public SwitchValidationResponse(@JsonProperty("rules") RulesValidationEntry rules,
                                    @JsonProperty("meters") MetersValidationEntry meters) {
        this.rules = rules;
        this.meters = meters;
    }
}
