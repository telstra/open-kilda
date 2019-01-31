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

package org.openkilda.messaging.model.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureTogglesDto {
    @JsonProperty("flows_reroute_on_isl_discovery")
    private Boolean flowsRerouteOnIslDiscoveryEnabled;

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

    public FeatureTogglesDto() {
    }

    public FeatureTogglesDto(@JsonProperty("flows_reroute_on_isl_discovery") Boolean flowsRerouteOnIslDiscoveryEnabled,
                             @JsonProperty("create_flow") Boolean createFlowEnabled,
                             @JsonProperty("update_flow") Boolean updateFlowEnabled,
                             @JsonProperty("delete_flow") Boolean deleteFlowEnabled,
                             @JsonProperty("push_flow") Boolean pushFlowEnabled,
                             @JsonProperty("unpush_flow") Boolean unpushFlowEnabled) {
        this.flowsRerouteOnIslDiscoveryEnabled = flowsRerouteOnIslDiscoveryEnabled;
        this.createFlowEnabled = createFlowEnabled;
        this.updateFlowEnabled = updateFlowEnabled;
        this.deleteFlowEnabled = deleteFlowEnabled;
        this.pushFlowEnabled = pushFlowEnabled;
        this.unpushFlowEnabled = unpushFlowEnabled;
    }
}
