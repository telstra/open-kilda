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

package org.openkilda.messaging.info.switches.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class MeterInfoEntryV2 implements Serializable {
    @JsonProperty("meter_id")
    private Long meterId;

    @JsonProperty("cookie")
    private Long cookie;

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("flow_path")
    private String flowPath;

    @JsonProperty("y_flow_id")
    private String yFlowId;

    @JsonProperty("rate")
    private Long rate;

    @JsonProperty("burst_size")
    private Long burstSize;

    @JsonProperty("flags")
    private List<String> flags;

    @JsonCreator
    public MeterInfoEntryV2(@JsonProperty("meter_id") Long meterId,
                            @JsonProperty("cookie") Long cookie,
                            @JsonProperty("flow_id") String flowId,
                            @JsonProperty("y_flow_id") String yFlowId,
                            @JsonProperty("flow_path") String flowPath,
                            @JsonProperty("rate") Long rate,
                            @JsonProperty("burst_size") Long burstSize,
                            @JsonProperty("flags") List<String> flags) {
        this.meterId = meterId;
        this.cookie = cookie;
        this.flowId = flowId;
        this.yFlowId = yFlowId;
        this.flowPath = flowPath;
        this.rate = rate;
        this.burstSize = burstSize;
        this.flags = flags;
    }
}
