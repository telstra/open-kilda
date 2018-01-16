package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class FlowResponse.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "source_switch", "src_port", "src_vlan", "target_switch", "dst_port",
        "dst_vlan", "maximum_bandwidth", "status", "description", "last-updated"})
public class FlowInfo implements Serializable {

    /** The flowid. */
    @JsonProperty("flowid")
    private String flowid;

    /** The source switch. */
    @JsonProperty("source_switch")
    private String sourceSwitch;

    /** The src port. */
    @JsonProperty("src_port")
    private int srcPort;

    /** The src vlan. */
    @JsonProperty("src_vlan")
    private int srcVlan;

    /** The target switch. */
    @JsonProperty("target_switch")
    private String targetSwitch;

    /** The dst port. */
    @JsonProperty("dst_port")
    private int dstPort;

    /** The dst vlan. */
    @JsonProperty("dst_vlan")
    private int dstVlan;

    /** The maximum bandwidth. */
    @JsonProperty("maximum_bandwidth")
    private int maximumBandwidth;

    /** The status. */
    @JsonProperty("status")
    private String status;

    /** The description. */
    @JsonProperty("description")
    private String description;

    /** The last updated. */
    @JsonProperty("last-updated")
    private String lastUpdated;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -7015976328478701934L;

    /**
     * Gets the flowid.
     *
     * @return the flowid
     */
    @JsonProperty("flowid")
    public String getFlowid() {
        return flowid;
    }

    /**
     * Sets the flowid.
     *
     * @param flowid the new flowid
     */
    @JsonProperty("flowid")
    public void setFlowid(final String flowid) {
        this.flowid = flowid;
    }

    /**
     * Gets the source switch.
     *
     * @return the source switch
     */
    @JsonProperty("source_switch")
    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Sets the source switch.
     *
     * @param sourceSwitch the new source switch
     */
    @JsonProperty("source_switch")
    public void setSourceSwitch(final String sourceSwitch) {
        this.sourceSwitch = sourceSwitch;
    }

    /**
     * Gets the src port.
     *
     * @return the src port
     */
    @JsonProperty("src_port")
    public int getSrcPort() {
        return srcPort;
    }

    /**
     * Sets the src port.
     *
     * @param srcPort the new src port
     */
    @JsonProperty("src_port")
    public void setSrcPort(final int srcPort) {
        this.srcPort = srcPort;
    }

    /**
     * Gets the src vlan.
     *
     * @return the src vlan
     */
    @JsonProperty("src_vlan")
    public int getSrcVlan() {
        return srcVlan;
    }

    /**
     * Sets the src vlan.
     *
     * @param srcVlan the new src vlan
     */
    @JsonProperty("src_vlan")
    public void setSrcVlan(final int srcVlan) {
        this.srcVlan = srcVlan;
    }

    /**
     * Gets the target switch.
     *
     * @return the target switch
     */
    @JsonProperty("target_switch")
    public String getTargetSwitch() {
        return targetSwitch;
    }

    /**
     * Sets the target switch.
     *
     * @param targetSwitch the new target switch
     */
    @JsonProperty("target_switch")
    public void setTargetSwitch(final String targetSwitch) {
        this.targetSwitch = targetSwitch;
    }

    /**
     * Gets the dst port.
     *
     * @return the dst port
     */
    @JsonProperty("dst_port")
    public int getDstPort() {
        return dstPort;
    }

    /**
     * Sets the dst port.
     *
     * @param dstPort the new dst port
     */
    @JsonProperty("dst_port")
    public void setDstPort(final int dstPort) {
        this.dstPort = dstPort;
    }

    /**
     * Gets the dst vlan.
     *
     * @return the dst vlan
     */
    @JsonProperty("dst_vlan")
    public int getDstVlan() {
        return dstVlan;
    }

    /**
     * Sets the dst vlan.
     *
     * @param dstVlan the new dst vlan
     */
    @JsonProperty("dst_vlan")
    public void setDstVlan(final int dstVlan) {
        this.dstVlan = dstVlan;
    }

    /**
     * Gets the maximum bandwidth.
     *
     * @return the maximum bandwidth
     */
    @JsonProperty("maximum_bandwidth")
    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Sets the maximum bandwidth.
     *
     * @param maximumBandwidth the new maximum bandwidth
     */
    @JsonProperty("maximum_bandwidth")
    public void setMaximumBandwidth(final int maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    @JsonProperty("status")
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    @JsonProperty("description")
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Gets the last updated.
     *
     * @return the last updated
     */
    @JsonProperty("last-updated")
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the last updated.
     *
     * @param lastUpdated the new last updated
     */
    @JsonProperty("last-updated")
    public void setLastUpdated(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

}
