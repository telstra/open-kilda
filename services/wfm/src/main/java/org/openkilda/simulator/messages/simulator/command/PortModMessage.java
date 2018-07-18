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
        "port-num",
        "active",
        "forwarding"})

public class PortModMessage extends SimulatorCommandMessage {
    @JsonProperty("port-num")
    private int portNum;

    @JsonProperty("active")
    private boolean isActive;

    @JsonProperty("forwarding")
    private boolean isForwarding;

    public PortModMessage(@JsonProperty("dpid") SwitchId dpid,
                          @JsonProperty("port-num") int portNum,
                          @JsonProperty("active") boolean isActive,
                          @JsonProperty("forwarding") boolean isForwarding) {
        this.dpid = dpid;
        this.portNum = portNum;
        this.isActive = isActive;
        this.isForwarding = isForwarding;
    }

    public int getPortNum() {
        return portNum;
    }

    public void setPortNum(int portNum) {
        this.portNum = portNum;
    }

    public boolean isActive() {
        return isActive;
    }

    public void isActive(boolean isActive) {
        this.isActive = isActive;
    }

    public boolean isForwarding() {
        return isForwarding;
    }

    public void isForwarding(boolean isForwarding) {
        this.isForwarding = isForwarding;
    }
}
