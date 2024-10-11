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
import org.openkilda.messaging.info.reroute.FlowType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowRerouteFlushRequest extends CommandData {
    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    String flowId;

    FlowType flowType;

    String reason;

    @JsonCreator
    public FlowRerouteFlushRequest(@NonNull @JsonProperty("flow_id") String flowId,
                                   @JsonProperty("flow_type") FlowType flowType,
                                   @JsonProperty("reason") String reason) {
        this.flowId = flowId;
        this.flowType = flowType;
        this.reason = reason;
    }
}
