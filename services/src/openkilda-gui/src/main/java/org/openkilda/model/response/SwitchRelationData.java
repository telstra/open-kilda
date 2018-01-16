package org.openkilda.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

import org.openkilda.model.FlowInfo;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;

/**
 * The Class SwitchRelationData.
 *
 * @author Gaurav Chugh
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switches", "switchrelation", "flows"})
public class SwitchRelationData implements Serializable {

    /** The switches. */
    @JsonProperty("switches")
    private List<SwitchInfo> switches = null;

    /** The switchrelation. */
    @JsonProperty("switchrelation")
    private List<IslLinkInfo> flows = null;

    /** The flows response. */
    @JsonProperty("flows")
    private List<FlowInfo> flowResponse = null;

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
    public void setSwitches(final List<SwitchInfo> switches) {
        this.switches = switches;
    }

    /**
     * Gets the switchrelation.
     *
     * @return the switchrelation
     */
    @JsonProperty("switchrelation")
    public List<IslLinkInfo> getFlows() {
        return flows;
    }

    /**
     * Sets the switchrelation.
     *
     * @param switchrelation the new switchrelation
     */
    @JsonProperty("switchrelation")
    public void setFlows(final List<IslLinkInfo> switchrelation) {
        flows = switchrelation;
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
    public void setPortInfo(final List<PortInfo> portInfo) {
        this.portInfo = portInfo;
    }

    /**
     * Gets the flow response.
     *
     * @return the flow response
     */
    @JsonProperty("flows")
    public List<FlowInfo> getFlowResponse() {
        return flowResponse;
    }

    /**
     * Sets the flow response.
     *
     * @param flowResponse the new flow response
     */
    @JsonProperty("flows")
    public void setFlowResponse(final List<FlowInfo> flowResponse) {
        this.flowResponse = flowResponse;
    }

}
