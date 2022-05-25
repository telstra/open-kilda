/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonNaming(value = SnakeCaseStrategy.class)
@ToString(callSuper = true)
public class UpdateYFlowStatsInfo extends BaseYFlowStatsInfo {
    @JsonCreator
    public UpdateYFlowStatsInfo(
            @JsonProperty("yflow_id") String yFlowId,
            @JsonProperty("shared_endpoint_resources") YFlowEndpointResources sharedEndpointResources,
            @JsonProperty("ypoint_resources") YFlowEndpointResources yPointResources,
            @JsonProperty("protected_ypoint_resources") YFlowEndpointResources protectedYPointResources) {
        super(yFlowId, sharedEndpointResources, yPointResources, protectedYPointResources);
    }

    public UpdateYFlowStatsInfo(BaseYFlowStatsInfo other) {
        super(other);
    }
}
