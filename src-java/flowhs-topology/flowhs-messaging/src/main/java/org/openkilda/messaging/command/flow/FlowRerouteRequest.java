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

import org.openkilda.messaging.command.CommandData;
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
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowRerouteRequest extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty("flowid")
    protected String flowId;

    @JsonProperty("path_ids")
    protected Set<IslEndpoint> affectedIsl;

    @JsonProperty("effectively_down")
    private boolean effectivelyDown;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth;

    @JsonProperty("manual")
    private boolean manual;

    /**
     * Create Simplified request usable only for northbound API.
     */
    public static FlowRerouteRequest createManualFlowRerouteRequest(String flowId, boolean force,
                                                                    boolean ignoreBandwidth, String reason) {
        return new FlowRerouteRequest(flowId, force, false, ignoreBandwidth, Collections.emptySet(), reason, true);
    }

    @JsonCreator
    public FlowRerouteRequest(@NonNull @JsonProperty("flowid") String flowId,
                              @JsonProperty("force") boolean force,
                              @JsonProperty("effectively_down") boolean effectivelyDown,
                              @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
                              @NonNull @JsonProperty("path_ids") Set<IslEndpoint> affectedIsl,
                              @JsonProperty("reason") String reason,
                              @JsonProperty("manual") boolean manual) {
        this.flowId = flowId;
        this.effectivelyDown = effectivelyDown;
        this.affectedIsl = affectedIsl;
        this.reason = reason;
        this.ignoreBandwidth = ignoreBandwidth;
        this.manual = manual;

        // field "force" have been removed as part of "true flow sync" feature. Constructor argument left for now, to
        // not ruin JSON compatibility for transition period.
    }

    public boolean isForce() {
        return false;  // dummy getter for removed "force" field.
    }
}
