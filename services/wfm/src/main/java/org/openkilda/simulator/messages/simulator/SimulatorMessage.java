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

package org.openkilda.simulator.messages.simulator;

import org.openkilda.simulator.classes.SimulatorCommands;
import org.openkilda.simulator.messages.simulator.command.AddLinkCommandMessage;
import org.openkilda.simulator.messages.simulator.command.AddSwitchCommand;
import org.openkilda.simulator.messages.simulator.command.PortModMessage;
import org.openkilda.simulator.messages.simulator.command.SwitchModMessage;
import org.openkilda.simulator.messages.simulator.command.TopologyMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TopologyMessage.class, name = SimulatorCommands.TOPOLOGY),
        @JsonSubTypes.Type(value = SwitchModMessage.class, name = SimulatorCommands.DO_SWITCH_MOD),
        @JsonSubTypes.Type(value = AddLinkCommandMessage.class, name = SimulatorCommands.DO_ADD_LINK),
        @JsonSubTypes.Type(value = PortModMessage.class, name = SimulatorCommands.DO_PORT_MOD),
        @JsonSubTypes.Type(value = AddSwitchCommand.class, name = SimulatorCommands.DO_ADD_SWITCH)
})

public class SimulatorMessage implements Serializable {
    private static final long serialVersionUID = 1L;
}
