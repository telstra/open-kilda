/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.nbtopology.request;

import org.openkilda.messaging.nbtopology.annotations.ReadRequest;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.Duration;

@EqualsAndHashCode(callSuper = true)
@ReadRequest
@Value
public class GetPathsRequest extends BaseRequest {

    @JsonProperty("src_switch_id")
    SwitchId srcSwitchId;

    @JsonProperty("dst_switch_id")
    SwitchId dstSwitchId;

    @JsonProperty("encapsulation_type")
    FlowEncapsulationType encapsulationType;

    @JsonProperty("path_computation_strategy")
    PathComputationStrategy pathComputationStrategy;

    @JsonProperty("max_latency")
    Duration maxLatency;

    @JsonProperty("max_latency_tier2")
    Duration maxLatencyTier2;

    @JsonProperty("max_path_count")
    Integer maxPathCount;

    public GetPathsRequest(@JsonProperty("src_switch_id") SwitchId srcSwitchId,
                           @JsonProperty("dst_switch_id") SwitchId dstSwitchId,
                           @JsonProperty("encapsulation_type") FlowEncapsulationType encapsulationType,
                           @JsonProperty("path_computation_strategy") PathComputationStrategy pathComputationStrategy,
                           @JsonProperty("max_latency") Duration maxLatency,
                           @JsonProperty("max_latency_tier2") Duration maxLatencyTier2,
                           @JsonProperty("max_path_count") Integer maxPathCount) {
        this.srcSwitchId = srcSwitchId;
        this.dstSwitchId = dstSwitchId;
        this.encapsulationType = encapsulationType;
        this.pathComputationStrategy = pathComputationStrategy;
        this.maxLatency = maxLatency;
        this.maxLatencyTier2 = maxLatencyTier2;
        this.maxPathCount = maxPathCount;
    }
}
