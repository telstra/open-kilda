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

package org.openkilda.messaging.model;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

public class NetworkEndpoint extends AbstractNetworkEndpoint {
    @Builder
    @JsonCreator
    public NetworkEndpoint(
            @JsonProperty("switch-id") SwitchId datapath,
            @JsonProperty("port-id") Integer portNumber) {
        super(datapath, portNumber);

        validateDatapath();
        validatePortNumber();
    }

    public NetworkEndpoint(NetworkEndpoint that) {
        this(that.getDatapath(), that.getPortNumber());
    }

    @JsonIgnore
    @Deprecated
    public SwitchId getSwitchDpId() {
        return getDatapath();
    }

    @JsonIgnore
    @Deprecated
    public Integer getPortId() {
        return getPortNumber();
    }
}
