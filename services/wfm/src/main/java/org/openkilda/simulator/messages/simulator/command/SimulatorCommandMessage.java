package org.openkilda.simulator.messages.simulator.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.simulator.messages.simulator.SimulatorMessage;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid"})

public abstract class SimulatorCommandMessage extends SimulatorMessage {
    @JsonProperty("dpid")
    protected String dpid;

    public SimulatorCommandMessage() {}

    public SimulatorCommandMessage(@JsonProperty("dpid") String dpid) {
        this.dpid = dpid;
    }

    public String getDpid() {
        return dpid;
    }

    public void setDpid(String dpid) {
        this.dpid = dpid;
    }
}
