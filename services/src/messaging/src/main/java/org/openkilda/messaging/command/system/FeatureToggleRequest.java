/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.openkilda.messaging.command.CommandData;

@Value
@Builder
@JsonInclude(Include.NON_NULL)
public class FeatureToggleRequest extends CommandData {

    @JsonProperty(value = "sync_rules")
    private Boolean syncRulesEnabled;

    @JsonProperty(value = "reflow_on_switch_activation")
    private Boolean reflowOnSwitchActivationEnabled;

    @JsonProperty(value = "child_correlation_id")
    private String childCorrelationId;

    public FeatureToggleRequest(
            @JsonProperty(value = "sync_rules") Boolean syncRulesEnabled,
            @JsonProperty(value = "reflow_on_switch_activation") Boolean reflowOnSwitchActivationEnabled,
            @JsonProperty(value = "child_correlation_id") String childCorrelationId) {
        this.syncRulesEnabled = syncRulesEnabled;
        this.reflowOnSwitchActivationEnabled = reflowOnSwitchActivationEnabled;
        this.childCorrelationId = childCorrelationId;
    }
}
