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
