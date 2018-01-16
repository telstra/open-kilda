package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class Destination.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switch-id", "port-id", "vlan-id"})
public class FlowEndpoint implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /** The switch id. */
    @JsonProperty("switch-id")
    private String switchId;

    /** The port id. */
    @JsonProperty("port-id")
    private int portId;

    /** The vlan id. */
    @JsonProperty("vlan-id")
    private int vlanId;

    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(String switchId) {
        this.switchId = switchId;
    }

    public int getPortId() {
        return portId;
    }

    public void setPortId(int portId) {
        this.portId = portId;
    }

    public int getVlanId() {
        return vlanId;
    }

    public void setVlanId(int vlanId) {
        this.vlanId = vlanId;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    @Override
    public String toString() {
        return "FlowEndpoint [switchId=" + switchId + ", portId=" + portId + ", vlanId=" + vlanId
                + "]";
    }
}
