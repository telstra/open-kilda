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

package org.openkilda.messaging.command.switches;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class PortConfigurationRequest extends CommandData {

    private static final long serialVersionUID = 7393431355263735216L;

    @NonNull
    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("port_no")
    private int portNumber;

    @JsonProperty("admin_down")
    private Boolean adminDown;

    public PortConfigurationRequest(
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("port_no") int portNumber, 
            @JsonProperty("admin_down") Boolean adminDown) {
        this.switchId = switchId;
        this.portNumber = portNumber;
        this.adminDown = adminDown;
    }
}
