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

package org.openkilda.floodlight.flow.response;

import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowRuleResponse extends FlowResponse {

    @JsonProperty("cookie")
    private Cookie cookie;

    @JsonProperty("in_port")
    private Integer inPort;

    @JsonProperty("out_port")
    private Integer outPort;

    @JsonProperty("in_vlan")
    private Integer inVlan;

    @JsonProperty("out_vlan")
    private Integer outVlan;

    @JsonProperty("meter_id")
    private MeterId meterId;

    @JsonProperty("OF_version")
    private String ofVersion;

    @JsonCreator
    @Builder(builderMethodName = "flowRuleResponseBuilder")
    public FlowRuleResponse(@JsonProperty("command_context") MessageContext messageContext,
                            @JsonProperty("command_id") UUID commandId,
                            @JsonProperty(FLOW_ID) String flowId,
                            @JsonProperty("switch_id") SwitchId switchId,
                            @JsonProperty("cookie") Cookie cookie,
                            @JsonProperty("in_port") Integer inPort,
                            @JsonProperty("out_port") Integer outPort,
                            @JsonProperty("in_vlan") Integer inVlan,
                            @JsonProperty("out_vlan") Integer outVlan,
                            @JsonProperty("meter_id") MeterId meterId,
                            @JsonProperty("OF_version") String ofVersion) {
        super(true, messageContext, commandId, flowId, switchId);

        this.cookie = cookie;
        this.inPort = inPort;
        this.outPort = outPort;
        this.inVlan = inVlan;
        this.outVlan = outVlan;
        this.meterId = meterId;
        this.ofVersion = ofVersion;
    }
}
