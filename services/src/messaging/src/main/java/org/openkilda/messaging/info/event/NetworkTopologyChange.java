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

package org.openkilda.messaging.info.event;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NetworkTopologyChange extends InfoData {
    @JsonProperty("type")
    private final NetworkTopologyChangeType type;

    @JsonProperty("switch_id")
    private final String switchId;

    @JsonProperty("port_number")
    private final int portNumber;

    @JsonCreator
    public NetworkTopologyChange(
            @JsonProperty("type") NetworkTopologyChangeType type,
            @JsonProperty("switch_id") String switchId,
            @JsonProperty("port_number") int portNumber) {
        this.type = type;
        this.switchId = switchId;
        this.portNumber = portNumber;
    }

    public NetworkTopologyChangeType getType() {
        return type;
    }

    public String getSwitchId() {
        return switchId;
    }

    public int getPortNumber() {
        return portNumber;
    }
}
