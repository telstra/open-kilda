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
import lombok.Singular;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

// FIXME(surabujin): combination of push/pop/set actions for vlans have no sense without their order, at this
//  moment this "view" of OF apply-actions do not preserve order...
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class FlowApplyActions implements Serializable {

    @JsonProperty("output")
    private String flowOutput;

    @Singular
    @JsonProperty("set_field")
    private List<FlowSetFieldAction> fieldActions;

    @JsonProperty("push_vlan")
    private String pushVlan;

    @JsonProperty("meter")
    private String meter;

    @JsonProperty("push_vxlan")
    private String pushVxlan;

    @JsonProperty("group")
    private String group;

    @JsonProperty("set_copy_field")
    private FlowCopyFieldAction copyFieldAction;

    @Singular
    @JsonProperty("encapsulation_actions")
    private List<String> encapsulationActions;

    @JsonCreator
    public FlowApplyActions(
            @JsonProperty("output") String flowOutput, @JsonProperty("set_field") List<FlowSetFieldAction> fieldActions,
            @JsonProperty("push_vlan") String pushVlan, @JsonProperty("meter") String meter,
            @JsonProperty("push_vxlan") String pushVxlan, @JsonProperty("group") String group,
            @JsonProperty("copy_field") FlowCopyFieldAction copyFieldAction,
            @JsonProperty("encapsulation_actions") List<String> encapsulationActions) {
        this.flowOutput = flowOutput;
        this.fieldActions = fieldActions;
        this.pushVlan = pushVlan;
        this.meter = meter;
        this.pushVxlan = pushVxlan;
        this.group = group;
        this.copyFieldAction = copyFieldAction;
        this.encapsulationActions = encapsulationActions;
    }
}
