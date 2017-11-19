package org.openkilda.simulator.messages.simulator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.simulator.classes.SimulatorCommands;
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
        @JsonSubTypes.Type(value = SwitchModMessage.class, name = SimulatorCommands.DO_SWITCH_MOD)})

public class SimulatorMessage implements Serializable {
    private static final long serialVersionUID = 1L;
}
