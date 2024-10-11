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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.util.List;

/**
 * Represents remove path info for HA-flow.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@JsonNaming(value = SnakeCaseStrategy.class)
@ToString(callSuper = true)
public class RemoveHaFlowPathInfo extends BaseHaFlowPathInfo {
    private static final long serialVersionUID = 1L;


    @JsonCreator
    public RemoveHaFlowPathInfo(@NonNull @JsonProperty("ha_flow_id") String haFlowId,
                                @NonNull @JsonProperty("cookie") FlowSegmentCookie cookie,
                                @JsonProperty("meter_id") MeterId meterId,
                                @NonNull @JsonProperty("path_nodes") List<PathNodePayload> pathNodes,
                                @JsonProperty("ingress_mirror") boolean ingressMirror,
                                @JsonProperty("egress_mirror") boolean egressMirror,
                                @JsonProperty("ypoint_group_id") GroupId yPointGroupId,
                                @JsonProperty("ypoint_meter_id") MeterId yPointMeterId,
                                @JsonProperty("ha_sub_flow_id") String haSubFlowId,
                                @JsonProperty("ypoint_switch_id") SwitchId yPointSwitchId,
                                @JsonProperty("shared_point_switch_id") SwitchId sharedPointSwitchId) {
        super(haFlowId, cookie, meterId, pathNodes,
                ingressMirror, egressMirror, yPointGroupId,
                yPointMeterId, haSubFlowId, yPointSwitchId, sharedPointSwitchId);
    }
}
