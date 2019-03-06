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

package org.openkilda.floodlight.flow.request;

import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InstallTransitRule extends InstallFlow {

    @JsonProperty("transit_vlan_id")
    protected Integer transitVlanId;

    @JsonCreator
    public InstallTransitRule(@JsonProperty("message_context") MessageContext messageContext,
                              @JsonProperty("command_id") String commandId,
                              @JsonProperty(FLOW_ID) String flowId,
                              @JsonProperty("cookie") Long cookie,
                              @JsonProperty("switch_id") SwitchId switchId,
                              @JsonProperty("input_port") Integer inputPort,
                              @JsonProperty("output_port") Integer outputPort,
                              @JsonProperty("transit_vlan_id") Integer transitVlanId) {
        super(messageContext, commandId, flowId, cookie, switchId, inputPort, outputPort);
        this.transitVlanId = transitVlanId;
    }
}
