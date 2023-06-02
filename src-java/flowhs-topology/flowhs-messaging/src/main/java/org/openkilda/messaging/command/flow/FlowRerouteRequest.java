/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.BaseRerouteRequest;
import org.openkilda.model.IslEndpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowRerouteRequest extends BaseRerouteRequest {
    private static final long serialVersionUID = 1L;

    String flowId;
    boolean effectivelyDown;
    boolean manual;

    /**
     * Create Simplified request usable only for northbound API.
     */
    public static FlowRerouteRequest createManualFlowRerouteRequest(String flowId,
                                                                    boolean ignoreBandwidth, String reason) {
        return new FlowRerouteRequest(flowId, false, ignoreBandwidth, Collections.emptySet(), reason, true);
    }

    @JsonCreator
    public FlowRerouteRequest(@NonNull @JsonProperty("flow_id") String flowId,
                              @JsonProperty("effectively_down") boolean effectivelyDown,
                              @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
                              @NonNull @JsonProperty("affected_isls") Set<IslEndpoint> affectedIsls,
                              @JsonProperty("reason") String reason,
                              @JsonProperty("manual") boolean manual) {
        super(affectedIsls, reason, ignoreBandwidth);
        this.flowId = flowId;
        this.effectivelyDown = effectivelyDown;
        this.manual = manual;
    }
}
