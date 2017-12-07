package org.openkilda.model;

import java.io.Serializable;
import java.util.List;

import org.openkilda.ws.response.FlowResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class SwitchRelationData.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"switches", "switchrelation", "flows"})
public class SwitchRelationData implements Serializable {

    /** The switches. */
    @JsonProperty("switches")
    private List<SwitchInfo> switches = null;

    /** The switchrelation. */
    @JsonProperty("switchrelation")
    private List<Switchrelation> switchrelation = null;

    /** The flow response. */
    @JsonProperty("flows")
    private List<FlowResponse> flowResponse = null;

    /** The port info. */
    @JsonProperty("ports")
    private List<PortInfo> portInfo = null;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -2510847461885912823L;

    /**
     * Gets the switches.
     *
     * @return the switches
     */
    @JsonProperty("switches")
    public List<SwitchInfo> getSwitches() {
        return switches;
    }

    /**
     * Sets the switches.
     *
     * @param switches the new switches
     */
    @JsonProperty("switches")
    public void setSwitches(List<SwitchInfo> switches) {
        this.switches = switches;
    }

    /**
     * Gets the switchrelation.
     *
     * @return the switchrelation
     */
    @JsonProperty("switchrelation")
    public List<Switchrelation> getSwitchrelation() {
        return switchrelation;
    }

    /**
     * Sets the switchrelation.
     *
     * @param switchrelation the new switchrelation
     */
    @JsonProperty("switchrelation")
    public void setSwitchrelation(List<Switchrelation> switchrelation) {
        this.switchrelation = switchrelation;
    }


    /**
     * Gets the flow response.
     *
     * @return the flow response
     */
    @JsonProperty("flows")
    public List<FlowResponse> getFlowResponse() {
        return flowResponse;
    }

    /**
     * Sets the flow response.
     *
     * @param flowResponse the new flow response
     */
    @JsonProperty("flows")
    public void setFlowResponse(List<FlowResponse> flowResponse) {
        this.flowResponse = flowResponse;
    }

    /**
     * Gets the port info.
     *
     * @return the port info
     */
    @JsonProperty("ports")
    public List<PortInfo> getPortInfo() {
        return portInfo;
    }

    /**
     * Sets the port info.
     *
     * @param portInfo the new port info
     */
    @JsonProperty("ports")
    public void setPortInfo(List<PortInfo> portInfo) {
        this.portInfo = portInfo;
    }

}
