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

package org.openkilda.testlib.service.floodlight.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class FlowApplyActions implements Serializable {

    private String flowOutput;
    private String field;
    private String pushVlan;
    private String popVlan;
    private String meter;

    @JsonCreator
    public FlowApplyActions(
            @JsonProperty("OUTPUT") String flowOutput, @JsonProperty("SET_FIELD") String field,
            @JsonProperty("PUSH_VLAN") String pushVlan, @JsonProperty("POP_VLAN") String popVlan,
            @JsonProperty("METER") String meter) {
        this.flowOutput = flowOutput;
        this.field = field;
        this.pushVlan = pushVlan;
        this.popVlan = popVlan;
        this.meter = meter;
    }

}
