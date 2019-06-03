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

package org.openkilda.messaging.info.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class FlowApplyActions implements Serializable {

    @JsonProperty("output")
    private String flowOutput;
    @JsonProperty("set_field")
    private FlowSetFieldAction fieldAction;
    @JsonProperty("push_vlan")
    private String pushVlan;
    @JsonProperty("POP_VLAN")
    private String popVlan;
    @JsonProperty("meter")
    private String meter;
    @JsonProperty("push_vxlan")
    private String pushVxlan;

    @JsonCreator
    public FlowApplyActions(
            @JsonProperty("output") String flowOutput, @JsonProperty("set_field") FlowSetFieldAction fieldAction,
            @JsonProperty("push_vlan") String pushVlan, @JsonProperty("POP_VLAN") String popVlan,
            @JsonProperty("meter") String meter, @JsonProperty("push_vxlan") String pushVxlan) {
        this.flowOutput = flowOutput;
        this.fieldAction = fieldAction;
        this.pushVlan = pushVlan;
        this.popVlan = popVlan;
        this.meter = meter;
        this.pushVxlan = pushVxlan;
    }

}
