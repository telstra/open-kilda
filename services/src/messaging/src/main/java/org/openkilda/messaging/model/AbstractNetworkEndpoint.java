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

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public abstract class AbstractNetworkEndpoint implements Serializable {
    @JsonProperty("switch-id")
    private SwitchId datapath;

    @JsonProperty("port-id")
    private Integer portNumber;

    @JsonCreator
    public AbstractNetworkEndpoint(SwitchId datapath, Integer portNumber) {
        this.datapath = datapath;
        this.portNumber = portNumber;
    }

    final void validateDatapath() {
        if (!Utils.validateSwitchId(datapath)) {
            throw new IllegalArgumentException(String.format("Invalid switch DPID: %s", datapath));
        }
    }

    final void validatePortNumber() {
        if (portNumber == null || portNumber < 0) {
            throw new IllegalArgumentException(String.format("Invalid portId: %s", portNumber));
        }
    }

}
