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

package org.openkilda.messaging.info.system;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FeatureTogglesResponse extends InfoData {

    @JsonProperty(value = "sync_rules")
    private Boolean syncRulesEnabled;

    @JsonProperty(value = "reflow_on_switch_activation")
    private Boolean reflowOnSwitchActivationEnabled;

    @JsonProperty("create_flow")
    private Boolean createFlowEnabled;

    @JsonProperty("update_flow")
    private Boolean updateFlowEnabled;

    @JsonProperty("delete_flow")
    private Boolean deleteFlowEnabled;

    @JsonProperty("push_flow")
    private Boolean pushFlowEnabled;

    @JsonProperty("unpush_flow")
    private Boolean unpushFlowEnabled;

    public FeatureTogglesResponse(@JsonProperty(value = "sync_rules") Boolean syncRulesEnabled,
            @JsonProperty(value = "reflow_on_switch_activation") Boolean reflowOnSwitchActivationEnabled,
            @JsonProperty("create_flow") Boolean createFlowEnabled,
            @JsonProperty("update_flow") Boolean updateFlowEnabled,
            @JsonProperty("delete_flow") Boolean deleteFlowEnabled, @JsonProperty("push_flow") Boolean pushFlowEnabled,
            @JsonProperty("unpush_flow") Boolean unpushFlowEnabled) {
        this.syncRulesEnabled = syncRulesEnabled;
        this.reflowOnSwitchActivationEnabled = reflowOnSwitchActivationEnabled;
        this.createFlowEnabled = createFlowEnabled;
        this.updateFlowEnabled = updateFlowEnabled;
        this.deleteFlowEnabled = deleteFlowEnabled;
        this.pushFlowEnabled = pushFlowEnabled;
        this.unpushFlowEnabled = unpushFlowEnabled;
    }

    public Boolean getSyncRulesEnabled() {
        return syncRulesEnabled;
    }

    public Boolean getReflowOnSwitchActivationEnabled() {
        return reflowOnSwitchActivationEnabled;
    }

    public Boolean getCreateFlowEnabled() {
        return createFlowEnabled;
    }

    public Boolean getUpdateFlowEnabled() {
        return updateFlowEnabled;
    }

    public Boolean getDeleteFlowEnabled() {
        return deleteFlowEnabled;
    }

    public Boolean getPushFlowEnabled() {
        return pushFlowEnabled;
    }

    public Boolean getUnpushFlowEnabled() {
        return unpushFlowEnabled;
    }

    @Override
    public String toString() {
        return "FeatureTogglesResponse [syncRulesEnabled=" + syncRulesEnabled + ", reflowOnSwitchActivationEnabled="
                + reflowOnSwitchActivationEnabled + ", createFlowEnabled=" + createFlowEnabled + ", updateFlowEnabled="
                + updateFlowEnabled + ", deleteFlowEnabled=" + deleteFlowEnabled + ", pushFlowEnabled="
                + pushFlowEnabled + ", unpushFlowEnabled=" + unpushFlowEnabled + "]";
    }

}
