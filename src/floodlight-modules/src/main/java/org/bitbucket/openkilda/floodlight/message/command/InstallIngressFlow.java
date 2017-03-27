package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.IOException;

/**
 * Created by jonv on 23/3/17.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "flow_name",
        "switch_id",
        "input_port",
        "output_port",
        "input_vlan_id",
        "transit_vlan_id",
        "bandwidth"
})

public class InstallIngressFlow extends CommandData {
    private String flowName;
    private String switchId;
    private Number inputPort;
    private Number outputPort;
    private Number transitVlanId;
    private Number bandwidth;

    @JsonProperty("input_vlan_id")  // set here and not in constructor as it is optional
    private Number inputVlanId = 0;

    @JsonCreator
    public InstallIngressFlow(@JsonProperty("flow_name") String flowName,
                              @JsonProperty("switch_id") String switchId,
                              @JsonProperty("input_port") Number inputPort,
                              @JsonProperty("output_port") Number outputPort,
                              @JsonProperty("transit_vlan_id") Number transitVlanId,
                              @JsonProperty("bandwidth") Number bandwidth) throws IOException {
        if (flowName == null) {
            throw new IOException("need to set a flow_name");
        }
        this.flowName = flowName;

        if (switchId == null) {
            throw new IOException("need to set a switch_id");
        }
        this.switchId = switchId;

        if (inputPort == null) {
            throw new IOException("need to set input_port");
        }
        this.inputPort = inputPort;

        if (outputPort == null) {
            throw new IOException("need to set output_port");
        }
        this.outputPort = outputPort;

        if (transitVlanId == null) {
            throw new IOException("need to set transit_vlan_id");
        }
        this.transitVlanId = transitVlanId;

        if (bandwidth == null) {
            throw new IOException("need to set bandwidth");
        }
        this.bandwidth = bandwidth;
    }

    @JsonProperty("input_vlan_id")
    public void setInputVlanId(Number vlanId) {
        this.inputVlanId = vlanId;
    }

    @JsonProperty("flow_name")
    public String getFlowName() {
        return flowName;
    }

    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    @JsonProperty("input_port")
    public Number getInputPort() {
        return inputPort;
    }

    @JsonProperty("output_port")
    public Number getOutputPort() {
        return outputPort;
    }

    @JsonProperty("transit_vlan_id")
    public Number getTransitVlanId() {
        return transitVlanId;
    }

    @JsonProperty("bandwidth")
    public Number getBandwidth() {
        return bandwidth;
    }

    @JsonProperty("input_vlan_id")
    public Number getInputVlanId() {
        return inputVlanId;
    }
}

