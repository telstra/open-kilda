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

import org.openkilda.model.SwitchId;
import org.openkilda.simulator.messages.simulator.SimulatorMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid"})

public abstract class SimulatorCommandMessage extends SimulatorMessage {
    @JsonProperty("dpid")
    protected SwitchId dpid;

    public SimulatorCommandMessage() {}

    public SimulatorCommandMessage(@JsonProperty("dpid") SwitchId dpid) {
        this.dpid = dpid;
    }

    public SwitchId getDpid() {
        return dpid;
    }

    public void setDpid(SwitchId dpid) {
        this.dpid = dpid;
    }
}
