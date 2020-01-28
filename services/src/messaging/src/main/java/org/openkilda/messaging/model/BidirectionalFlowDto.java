/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.model;

import org.openkilda.messaging.payload.flow.FlowState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
public class BidirectionalFlowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    private String flowId;

    // FIXME(surabujin): String field is worse possible representation of time
    // private String lastUpdated;

    @JsonProperty("bandwidth")
    private long bandwidth;

    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth;

    @JsonProperty("periodic-pings")
    private boolean periodicPings;

    @JsonProperty("cookie")
    private long cookie;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private FlowState state;

    @JsonProperty("pinned")
    private boolean pinned;

    @JsonProperty("forward")
    private FlowDto forward;

    @JsonProperty("reverse")
    private FlowDto reverse;

    public BidirectionalFlowDto(FlowPairDto<FlowDto, FlowDto> flowPair) {
        this(flowPair.getLeft(), flowPair.getRight());
    }

    public BidirectionalFlowDto(FlowDto forward, FlowDto reverse) {
        this(forward.getFlowId(),
                forward.getBandwidth(),
                forward.isIgnoreBandwidth(),
                Boolean.TRUE.equals(forward.getPeriodicPings()),
                forward.getFlagglessCookie(),
                forward.getDescription(),
                forward.getState(),
                forward.isPinned(),
                forward, reverse);
    }

    @Builder
    @JsonCreator
    public BidirectionalFlowDto(
            @JsonProperty("flow_id")  String flowId,
            @JsonProperty("bandwidth") long bandwidth,
            @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
            @JsonProperty("periodic-pings") boolean periodicPings,
            @JsonProperty("cookie") long cookie,
            @JsonProperty("description") String description,
            @JsonProperty("state") FlowState state,
            @JsonProperty("pinned") boolean pinned,
            @JsonProperty("forward") FlowDto forward,
            @JsonProperty("reverse") FlowDto reverse) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.periodicPings = periodicPings;
        this.cookie = cookie;
        this.description = description;
        this.state = state;
        this.forward = forward;
        this.reverse = reverse;
        this.pinned = pinned;
    }
}
