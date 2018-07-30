/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
public class BidirectionalFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    private String flowId;

    // FIXME(surabujin): String field is worse possible representation of time
    // private String lastUpdated;

    @JsonProperty("bandwidth")
    private long bandwidth;
    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth;

    @JsonProperty("cookie")
    private long cookie;

    @JsonProperty("description")
    private String description;

    @JsonProperty("forward")
    private Flow forward;

    @JsonProperty("reverse")
    private Flow reverse;

    public BidirectionalFlow(ImmutablePair<Flow, Flow> flowPair) {
        this(flowPair.getLeft(), flowPair.getRight());
    }

    public BidirectionalFlow(Flow forward, Flow reverse) {
        flowId = forward.getFlowId();
        bandwidth = forward.getBandwidth();
        ignoreBandwidth = forward.isIgnoreBandwidth();
        cookie = forward.getFlagglessCookie();
        description = forward.getDescription();

        this.forward = forward;
        this.reverse = reverse;
    }

    @Builder
    @JsonCreator
    public BidirectionalFlow(
            @JsonProperty("flow_id")  String flowId,
            @JsonProperty("bandwidth") long bandwidth,
            @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
            @JsonProperty("cookie") long cookie,
            @JsonProperty("description") String description,
            @JsonProperty("forward") Flow forward,
            @JsonProperty("reverse") Flow reverse) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.cookie = cookie;
        this.description = description;
        this.forward = forward;
        this.reverse = reverse;
    }
}
