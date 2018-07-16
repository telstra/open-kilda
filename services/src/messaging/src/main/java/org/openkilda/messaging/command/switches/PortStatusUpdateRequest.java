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
import lombok.Value;
import org.openkilda.messaging.command.CommandData;

import java.util.List;

@Value
public class PortStatusUpdateRequest extends CommandData {

    private static final long serialVersionUID = 7393431355263735216L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("port_id")
    private String portId;

    @JsonProperty("status")
    private PortStatus status;

    public PortStatusUpdateRequest(
        @JsonProperty("switch_id") String switchId,
        @JsonProperty("port_id") String portId, @JsonProperty("status") PortStatus status) {
       this.switchId = switchId;
       this.portId = portId;
       this.status = status;
    }
}

