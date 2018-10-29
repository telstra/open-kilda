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

package org.openkilda.messaging.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FeatureTogglePayload {

    @JsonProperty("sync_rules")
    private Boolean syncRulesEnabled;

    @JsonProperty("reroute_on_isl_discovery")
    private Boolean rerouteOnIslDiscoveryEnabled;

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

    public FeatureTogglePayload(@JsonProperty("sync_rules") Boolean syncRulesEnabled,
                                @JsonProperty("reroute_on_isl_discovery") Boolean rerouteOnIslDiscoveryEnabled,
                                @JsonProperty("create_flow") Boolean createFlowEnabled,
                                @JsonProperty("update_flow") Boolean updateFlowEnabled,
                                @JsonProperty("delete_flow") Boolean deleteFlowEnabled,
                                @JsonProperty("push_flow") Boolean pushFlowEnabled,
                                @JsonProperty("unpush_flow") Boolean unpushFlowEnabled) {
        this.syncRulesEnabled = syncRulesEnabled;
        this.rerouteOnIslDiscoveryEnabled = rerouteOnIslDiscoveryEnabled;
        this.createFlowEnabled = createFlowEnabled;
        this.updateFlowEnabled = updateFlowEnabled;
        this.deleteFlowEnabled = deleteFlowEnabled;
        this.pushFlowEnabled = pushFlowEnabled;
        this.unpushFlowEnabled = unpushFlowEnabled;
    }

    public FeatureTogglePayload(FeatureTogglePayload payload) {
        this.syncRulesEnabled = payload.syncRulesEnabled;
        this.rerouteOnIslDiscoveryEnabled = payload.rerouteOnIslDiscoveryEnabled;
        this.createFlowEnabled = payload.createFlowEnabled;
        this.updateFlowEnabled = payload.updateFlowEnabled;
        this.deleteFlowEnabled = payload.deleteFlowEnabled;
        this.pushFlowEnabled = payload.pushFlowEnabled;
        this.unpushFlowEnabled = payload.unpushFlowEnabled;
    }

    @Override
    public String toString() {
        return "FeatureTogglePayload [syncRulesEnabled=" + syncRulesEnabled + ", rerouteOnIslDiscoveryEnabled="
                + rerouteOnIslDiscoveryEnabled + ", createFlowEnabled=" + createFlowEnabled + ", updateFlowEnabled="
                + updateFlowEnabled + ", deleteFlowEnabled=" + deleteFlowEnabled + ", pushFlowEnabled="
                + pushFlowEnabled + ", unpushFlowEnabled=" + unpushFlowEnabled + "]";
    }

}
