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

package org.openkilda.messaging.history;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowData extends HistoryData {
    private String flowId;

    private long bandwidth;

    private boolean ignoreBandwidth;

    private long forwardCookie;

    private long reverseCookie;

    private String sourceSwitch;

    private String destinationSwitch;

    private int sourcePort;

    private int destinationPort;

    private int sourceVlan;

    private int destinationVlan;

    private Integer forwardMeterId;

    private Integer reverseMeterId;

    private String forwardPath;

    private String reversePath;

    private String forwardState;

    private String reverseState;

    @Builder
    @JsonCreator
    public FlowData(
            @JsonProperty("actor") String actor,
            @JsonProperty("action") String action,
            @JsonProperty("taskId") String taskId,
            @JsonProperty("details") String details,
            @JsonProperty("affectedFlows") List<String> affectedFlows,
            @JsonProperty("flowId") String flowId,
            @JsonProperty("bandwidth") long bandwidth,
            @JsonProperty("ignoreBandwidth") boolean ignoreBandwidth,
            @JsonProperty("forwardCookie") long forwardCookie,
            @JsonProperty("reverseCookie") long reverseCookie,
            @JsonProperty("sourceSwitch") String sourceSwitch,
            @JsonProperty("destinationSwitch") String destinationSwitch,
            @JsonProperty("sourcePort") int sourcePort,
            @JsonProperty("destinationPort") int destinationPort,
            @JsonProperty("sourceVlan") int sourceVlan,
            @JsonProperty("destinationVlan") int destinationVlan,
            @JsonProperty("forwardMeterId") Integer forwardMeterId,
            @JsonProperty("reverseMeterId") Integer reverseMeterId,
            @JsonProperty("forwardPath") String forwardPath,
            @JsonProperty("reversePath") String reversePath,
            @JsonProperty("forwardState") String forwardState,
            @JsonProperty("reverseState") String reverseState
    ) {
        super(actor, action, taskId, details, affectedFlows);
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.forwardCookie = forwardCookie;
        this.reverseCookie = reverseCookie;
        this.sourceSwitch = sourceSwitch;
        this.destinationSwitch = destinationSwitch;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sourceVlan = sourceVlan;
        this.destinationVlan = destinationVlan;
        this.forwardMeterId = forwardMeterId;
        this.reverseMeterId = reverseMeterId;
        this.forwardPath = forwardPath;
        this.reversePath = reversePath;
        this.forwardState = forwardState;
        this.reverseState = reverseState;
    }
}
