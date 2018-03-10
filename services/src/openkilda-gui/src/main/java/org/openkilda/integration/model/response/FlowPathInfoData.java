package org.openkilda.integration.model.response;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PathLinkResponse.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"bandwidth", "cookie", "description", "dst_port", "dst_switch", "dst_vlan",
        "flowid", "flowpath", "ignore_bandwidth", "last_updated", "meter_id", "src_port", "src_switch", "src_vlan",
        "transit_vlan"})
public class FlowPathInfoData {

    /** The bandwidth. */
    @JsonProperty("bandwidth")
    private Integer bandwidth;

    /** The cookie. */
    @JsonProperty("cookie")
    private Long cookie;

    /** The description. */
    @JsonProperty("description")
    private String description;

    /** The dst port. */
    @JsonProperty("dst_port")
    private Integer dstPort;

    /** The dst switch. */
    @JsonProperty("dst_switch")
    private String dstSwitch;

    /** The dst vlan. */
    @JsonProperty("dst_vlan")
    private Integer dstVlan;

    /** The flowid. */
    @JsonProperty("flowid")
    private String flowid;

    /** The flowpath. */
    @JsonProperty("flowpath")
    private PathInfoData flowpath;

    /** The ignore Bandwidth. */
    @JsonProperty("ignore_bandwidth")
    private Boolean ignoreBandwidth;
    
    /** The last updated. */
    @JsonProperty("last_updated")
    private String lastUpdated;

    /** The meter id. */
    @JsonProperty("meter_id")
    private Integer meterId;

    /** The src port. */
    @JsonProperty("src_port")
    private Integer srcPort;

    /** The src switch. */
    @JsonProperty("src_switch")
    private String srcSwitch;

    /** The src vlan. */
    @JsonProperty("src_vlan")
    private Integer srcVlan;

    /** The transit vlan. */
    @JsonProperty("transit_vlan")
    private Long transitVlan;

    /** The additional properties. */
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Gets the bandwidth.
     *
     * @return the bandwidth
     */
    @JsonProperty("bandwidth")
    public Integer getBandwidth() {
        return bandwidth;
    }

    /**
     * Sets the bandwidth.
     *
     * @param bandwidth the new bandwidth
     */
    @JsonProperty("bandwidth")
    public void setBandwidth(Integer bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * Gets the cookie.
     *
     * @return the cookie
     */
    @JsonProperty("cookie")
    public Long getCookie() {
        return cookie;
    }

    /**
     * Sets the cookie.
     *
     * @param cookie the new cookie
     */
    @JsonProperty("cookie")
    public void setCookie(Long cookie) {
        this.cookie = cookie;
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
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the dst port.
     *
     * @return the dst port
     */
    @JsonProperty("dst_port")
    public Integer getDstPort() {
        return dstPort;
    }

    /**
     * Sets the dst port.
     *
     * @param dstPort the new dst port
     */
    @JsonProperty("dst_port")
    public void setDstPort(Integer dstPort) {
        this.dstPort = dstPort;
    }

    /**
     * Gets the dst switch.
     *
     * @return the dst switch
     */
    @JsonProperty("dst_switch")
    public String getDstSwitch() {
        return dstSwitch;
    }

    /**
     * Sets the dst switch.
     *
     * @param dstSwitch the new dst switch
     */
    @JsonProperty("dst_switch")
    public void setDstSwitch(String dstSwitch) {
        this.dstSwitch = dstSwitch;
    }

    /**
     * Gets the dst vlan.
     *
     * @return the dst vlan
     */
    @JsonProperty("dst_vlan")
    public Integer getDstVlan() {
        return dstVlan;
    }

    /**
     * Sets the dst vlan.
     *
     * @param dstVlan the new dst vlan
     */
    @JsonProperty("dst_vlan")
    public void setDstVlan(Integer dstVlan) {
        this.dstVlan = dstVlan;
    }

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
    public void setFlowid(String flowid) {
        this.flowid = flowid;
    }

    /**
     * Gets the flowpath.
     *
     * @return the flowpath
     */
    @JsonProperty("flowpath")
    public PathInfoData getFlowpath() {
        return flowpath;
    }

    /**
     * Sets the flowpath.
     *
     * @param flowpath the new flowpath
     */
    @JsonProperty("flowpath")
    public void setFlowpath(PathInfoData flowpath) {
        this.flowpath = flowpath;
    }

    /**
     * Gets the Ignore Bandwidth.
     *
     * @return the ignoreBandwidth
     */
    public Boolean getIgnoreBandwidth() {
		return ignoreBandwidth;
	}

    /**
     * Sets the Ignore Bandwidth.
     *
     * @param ignoreBandwidth 
     */
	public void setIgnoreBandwidth(Boolean ignoreBandwidth) {
		this.ignoreBandwidth = ignoreBandwidth;
	}

	/**
     * Gets the last updated.
     *
     * @return the last updated
     */
    @JsonProperty("last_updated")
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the last updated.
     *
     * @param lastUpdated the new last updated
     */
    @JsonProperty("last_updated")
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Gets the meter id.
     *
     * @return the meter id
     */
    @JsonProperty("meter_id")
    public Integer getMeterId() {
        return meterId;
    }

    /**
     * Sets the meter id.
     *
     * @param meterId the new meter id
     */
    @JsonProperty("meter_id")
    public void setMeterId(Integer meterId) {
        this.meterId = meterId;
    }

    /**
     * Gets the src port.
     *
     * @return the src port
     */
    @JsonProperty("src_port")
    public Integer getSrcPort() {
        return srcPort;
    }

    /**
     * Sets the src port.
     *
     * @param srcPort the new src port
     */
    @JsonProperty("src_port")
    public void setSrcPort(Integer srcPort) {
        this.srcPort = srcPort;
    }

    /**
     * Gets the src switch.
     *
     * @return the src switch
     */
    @JsonProperty("src_switch")
    public String getSrcSwitch() {
        return srcSwitch;
    }

    /**
     * Sets the src switch.
     *
     * @param srcSwitch the new src switch
     */
    @JsonProperty("src_switch")
    public void setSrcSwitch(String srcSwitch) {
        this.srcSwitch = srcSwitch;
    }

    /**
     * Gets the src vlan.
     *
     * @return the src vlan
     */
    @JsonProperty("src_vlan")
    public Integer getSrcVlan() {
        return srcVlan;
    }

    /**
     * Sets the src vlan.
     *
     * @param srcVlan the new src vlan
     */
    @JsonProperty("src_vlan")
    public void setSrcVlan(Integer srcVlan) {
        this.srcVlan = srcVlan;
    }

    /**
     * Gets the transit vlan.
     *
     * @return the transit vlan
     */
    @JsonProperty("transit_vlan")
    public Long getTransitVlan() {
        return transitVlan;
    }

    /**
     * Sets the transit vlan.
     *
     * @param transitVlan the new transit vlan
     */
    @JsonProperty("transit_vlan")
    public void setTransitVlan(Long transitVlan) {
        this.transitVlan = transitVlan;
    }

    /**
     * Gets the additional properties.
     *
     * @return the additional properties
     */
    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    /**
     * Sets the additional property.
     *
     * @param name the name
     * @param value the value
     */
    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

	@Override
	public String toString() {
		return "PathLinkResponse [bandwidth=" + bandwidth + ", cookie="
				+ cookie + ", description=" + description + ", dstPort="
				+ dstPort + ", dstSwitch=" + dstSwitch + ", dstVlan=" + dstVlan
				+ ", flowid=" + flowid + ", flowpath=" + flowpath
				+ ", ignoreBandwidth=" + ignoreBandwidth + ", lastUpdated="
				+ lastUpdated + ", meterId=" + meterId + ", srcPort=" + srcPort
				+ ", srcSwitch=" + srcSwitch + ", srcVlan=" + srcVlan
				+ ", transitVlan=" + transitVlan + ", additionalProperties="
				+ additionalProperties + "]";
	}

}
