/* Copyright 2023 Telstra Open Source
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

package org.openkilda.messaging.payload.history;

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.FlowPathStatus;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowPathPayload {
    String haPathId;
    String cookie;
    String sharedPointMeterId;
    Long bandwidth;
    Boolean ignoreBandwidth;
    String timeCreate;
    String timeModify;
    FlowPathStatus status;
    String sharedSwitchId;

    @JsonProperty("y_point_switch_id")
    @JsonAlias("ypoint_switch_id")
    String yPointSwitchId;

    @JsonProperty("y_point_group_id")
    @JsonAlias("ypoint_group_id")
    String yPointGroupId;

    @JsonProperty("y_point_meter_id")
    @JsonAlias("ypoint_meter_id")
    String yPointMeterId;

    List<List<PathNodePayload>> paths;

    List<HaSubFlowPayload> haSubFlows;
}
