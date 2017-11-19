package org.openkilda.simulator.messages.simulator.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.simulator.classes.SwitchState;
import org.openkilda.simulator.messages.simulator.SimulatorMessage;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid",
        "state"})

public class SwitchModMessage extends SimulatorCommandMessage {

    @JsonProperty("state")
    private SwitchState state;

    public SwitchModMessage(@JsonProperty("dpid") String dpid,
                            @JsonProperty("state") SwitchState state) {
        this.state = state;
    }

    public SwitchState getState() {
        return state;
    }

    public void setState(SwitchState state) {
        this.state = state;
    }
}
