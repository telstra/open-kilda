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

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;
import org.openkilda.messaging.command.CommandData;

@Getter
@Setter
public class PortConfigurationRequest extends CommandData {

    private static final long serialVersionUID = 7393431355263735216L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("port_no")
    private int portNo;

    @JsonProperty("status")
    private String status;

    @JsonProperty("speed")
    private long speed;

    public PortConfigurationRequest(
            @JsonProperty("switch_id") String switchId, @JsonProperty("port_no") int portNo, 
            @JsonProperty("status") String status, @JsonProperty("speed") long speed) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.status = status;
        this.speed = speed;
    }
}
