/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class ReinstallServer42FlowForSwitchManagerRequest extends ReinstallDefaultFlowForSwitchManagerRequest {

    @JsonProperty("server42_mac_address")
    protected MacAddress server42MacAddress;

    @JsonProperty("server42_vlan")
    protected int server42Vlan;

    @JsonProperty("server42_port")
    protected int server42Port;

    public ReinstallServer42FlowForSwitchManagerRequest(
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("cookie") long cookie,
            @JsonProperty("server42_mac_address") MacAddress server42MacAddress,
            @JsonProperty("server42_vlan") int server42Vlan,
            @JsonProperty("server42_port") int server42Port) {
        super(switchId, cookie);
        this.server42MacAddress = server42MacAddress;
        this.server42Vlan = server42Vlan;
        this.server42Port = server42Port;
    }
}
