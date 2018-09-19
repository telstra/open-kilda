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

package org.openkilda.simulator.messages.simulator.command;

import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid",
        "num_of_ports"})

public class AddSwitchCommand extends SimulatorCommandMessage {
    @JsonProperty("dpid")
    private SwitchId dpid;

    @JsonProperty("num_of_ports")
    private int numOfPorts;

    public AddSwitchCommand(@JsonProperty("dpid") SwitchId dpid,
                            @JsonProperty("num_of_ports") int numOfPorts) {
        this.dpid = dpid;
        this.numOfPorts = numOfPorts;
    }

    public SwitchId getDpid() {
        return dpid;
    }

    public int getNumOfPorts() {
        return numOfPorts;
    }

    public void setNumOfPorts(int numOfPorts) {
        this.numOfPorts = numOfPorts;
    }
}
