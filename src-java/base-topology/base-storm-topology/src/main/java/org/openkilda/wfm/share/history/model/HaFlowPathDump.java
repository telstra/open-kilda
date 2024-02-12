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

package org.openkilda.wfm.share.history.model;

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowPathDump implements Serializable {
    PathId haPathId;
    SwitchId yPointSwitchId;
    FlowSegmentCookie cookie;
    MeterId yPointMeterId;
    MeterId sharedPointMeterId;
    GroupId yPointGroupId;
    Long bandwidth;
    Boolean ignoreBandwidth;
    Instant timeCreate;
    Instant timeModify;
    FlowPathStatus status;
    SwitchId sharedSwitchId;
    List<List<PathNodePayload>> paths;

    List<HaSubFlowDump> haSubFlows;

    /**
     * Creates an empty dump with initialized collections.
     * @return an empty dump
     */
    public static HaFlowPathDump empty() {
        return HaFlowPathDump.builder()
                .paths(Collections.emptyList())
                .haSubFlows(Collections.emptyList())
                .build();
    }
}
