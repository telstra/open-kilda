package org.openkilda.simulator.messages.simulator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.simulator.classes.SimulatorCommands;
import org.openkilda.simulator.messages.simulator.command.AddLinkCommandMessage;
import org.openkilda.simulator.messages.simulator.command.PortModMessage;
import org.openkilda.simulator.messages.simulator.command.SwitchModMessage;
import org.openkilda.simulator.messages.simulator.command.TopologyMessage;

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
        @JsonSubTypes.Type(value = PortModMessage.class, name = SimulatorCommands.DO_PORT_MOD)
})

public class SimulatorMessage implements Serializable {
    private static final long serialVersionUID = 1L;
}
