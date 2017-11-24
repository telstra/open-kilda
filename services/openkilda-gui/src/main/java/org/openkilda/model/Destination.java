package org.openkilda.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Destination.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"switch-id", "port-id", "vlan-id"})
public class Destination implements Serializable {

    /** The switch id. */
    @JsonProperty("switch-id")
    private String switchId;

    /** The port id. */
    @JsonProperty("port-id")
    private int portId;

    /** The vlan id. */
    @JsonProperty("vlan-id")
    private int vlanId;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -6686823029203245697L;

    /**
     * Gets the switch id.
     *
     * @return the switch id
     */
    @JsonProperty("switch-id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the switch id.
     *
     * @param switchId the new switch id
     */
    @JsonProperty("switch-id")
    public void setSwitchId(String switchId) {
        this.switchId = switchId;
    }

    /**
     * Gets the port id.
     *
     * @return the port id
     */
    @JsonProperty("port-id")
    public int getPortId() {
        return portId;
    }

    /**
     * Sets the port id.
     *
     * @param portId the new port id
     */
    @JsonProperty("port-id")
    public void setPortId(int portId) {
        this.portId = portId;
    }

    /**
     * Gets the vlan id.
     *
     * @return the vlan id
     */
    @JsonProperty("vlan-id")
    public int getVlanId() {
        return vlanId;
    }

    /**
     * Sets the vlan id.
     *
     * @param vlanId the new vlan id
     */
    @JsonProperty("vlan-id")
    public void setVlanId(int vlanId) {
        this.vlanId = vlanId;
    }

}
