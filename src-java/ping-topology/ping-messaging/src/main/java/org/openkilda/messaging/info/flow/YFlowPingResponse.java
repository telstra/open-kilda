/* Copyright 2022 Telstra Open Source
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

package org.openkilda.messaging.info.flow;

import static org.openkilda.messaging.info.flow.FlowPingResponseUtils.buildPingSuccess;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonNaming(SnakeCaseStrategy.class)
public class YFlowPingResponse extends InfoData {
    @JsonProperty("y_flow_id")
    String yFlowId;
    boolean pingSuccess;
    String error;
    List<SubFlowPingPayload> subFlows;

    public YFlowPingResponse(String yFlowId, String error, List<SubFlowPingPayload> subFlows) {
        this.yFlowId = yFlowId;
        this.error = error;
        this.subFlows = subFlows;
        this.pingSuccess = buildPingSuccess(error, subFlows);
    }
}
