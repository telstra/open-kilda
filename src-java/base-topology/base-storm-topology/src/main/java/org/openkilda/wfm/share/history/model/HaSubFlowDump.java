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

import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class HaSubFlowDump implements Serializable {
    String haFlowId;
    String haSubFlowId;
    FlowStatus status;
    SwitchId endpointSwitchId;
    Integer endpointPort;
    Integer endpointVlan;
    Integer endpointInnerVlan;
    String description;
    Instant timeCreate;
    Instant timeModify;

    public static HaSubFlowDump empty() {
        return HaSubFlowDump.builder().build();
    }
}
