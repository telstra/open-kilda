package org.openkilda.integration.model;

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

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch-id")
    private String switchId;

    @JsonProperty("port-id")
    private int portId;

    @JsonProperty("vlan-id")
    private int vlanId;
    
    @JsonProperty("switch-name")
    private String switchName;

    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    public int getPortId() {
        return portId;
    }

    public void setPortId(final int portId) {
        this.portId = portId;
    }

    public int getVlanId() {
        return vlanId;
    }

    public void setVlanId(final int vlanId) {
        this.vlanId = vlanId;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }
    
    public String getSwitchName() {
        return switchName;
    }

    public void setSwitchName(String switchName) {
        this.switchName = switchName;
    }
    
    @Override
    public String toString() {
        return "FlowEndpoint [switchId=" + switchId + ", portId=" + portId + ", vlanId=" + vlanId
                + "]";
    }
}
