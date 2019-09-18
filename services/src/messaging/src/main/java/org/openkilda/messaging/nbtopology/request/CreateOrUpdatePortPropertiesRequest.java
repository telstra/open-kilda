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

package org.openkilda.messaging.nbtopology.request;

import org.openkilda.messaging.nbtopology.annotations.UpdateRequest;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@SuppressWarnings("squid:MaximumInheritanceDepth")
@UpdateRequest
@Getter
@ToString
public class CreateOrUpdatePortPropertiesRequest extends SwitchesBaseRequest {

    @JsonProperty("switch_id")
    private SwitchId switchId;
    @JsonProperty("port")
    private int port;
    @JsonProperty("discovery_enabled")
    private boolean discoveryEnabled;

    public CreateOrUpdatePortPropertiesRequest(@JsonProperty("switch_id") SwitchId switchId,
                                               @JsonProperty("port") int port,
                                               @JsonProperty("discovery_enabled") boolean discoveryEnabled) {
        this.switchId = switchId;
        this.port = port;
        this.discoveryEnabled = discoveryEnabled;
    }
}
