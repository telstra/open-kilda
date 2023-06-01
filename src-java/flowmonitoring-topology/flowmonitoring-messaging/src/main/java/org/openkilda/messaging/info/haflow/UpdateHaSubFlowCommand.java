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

package org.openkilda.messaging.info.haflow;

import org.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Represents update ha flow in monitoring command.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateHaSubFlowCommand extends CommandData {
    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    String flowId;

    @JsonProperty("max_latency")
    Long maxLatency;

    @JsonProperty("max_latency_tier_2")
    Long maxLatencyTier2;

    @JsonCreator
    public UpdateHaSubFlowCommand(@JsonProperty("flow_id") String flowId,
                                  @JsonProperty("max_latency") Long maxLatency,
                                  @JsonProperty("max_latency_tier_2") Long maxLatencyTier2) {
        this.flowId = flowId;
        this.maxLatency = maxLatency;
        this.maxLatencyTier2 = maxLatencyTier2;
    }

    public Long getMaxLatency() {
        return resolveMaxLatency(maxLatency);
    }

    public Long getMaxLatencyTier2() {
        return resolveMaxLatency(maxLatencyTier2);
    }

    private Long resolveMaxLatency(Long maxLatency) {
        return maxLatency == null || maxLatency == 0 ? Long.MAX_VALUE : maxLatency;
    }
}
