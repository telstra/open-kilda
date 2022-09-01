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

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * Represents remove path info.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@JsonNaming(value = SnakeCaseStrategy.class)
public class RemoveFlowPathInfo extends BaseFlowPathInfo {
    private static final long serialVersionUID = 1L;

    @JsonCreator
    public RemoveFlowPathInfo(@NonNull @JsonProperty("flow_id") String flowId,
                              @JsonProperty("yflow_id") String yFlowId,
                              @NonNull @JsonProperty("cookie") FlowSegmentCookie cookie,
                              @JsonProperty("meter_id") MeterId meterId,
                              @NonNull @JsonProperty("path_nodes") List<PathNodePayload> pathNodes,
                              @JsonProperty("stat_vlans") Set<Integer> statVlans,
                              @JsonProperty("ingress_mirror") boolean ingressMirror,
                              @JsonProperty("egress_mirror") boolean egressMirror) {
        super(flowId, yFlowId, cookie, meterId, pathNodes, statVlans, ingressMirror, egressMirror);
    }
}
