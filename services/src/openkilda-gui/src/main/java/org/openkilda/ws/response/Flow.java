package org.openkilda.ws.response;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Flow.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "flowid", "source_switch", "src_port", "src_vlan",
		"target_switch", "dst_port", "dst_vlan", "maximum_bandwidth", "status",
		"description" })
public class Flow implements Serializable {

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

	/** The additional properties. */
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	/** The Constant serialVersionUID. */
	private final static long serialVersionUID = 4761185097162846611L;

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
	 * @param flowid
	 *            the new flowid
	 */
	@JsonProperty("flowid")
	public void setFlowid(String flowid) {
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
	 * @param sourceSwitch
	 *            the new source switch
	 */
	@JsonProperty("source_switch")
	public void setSourceSwitch(String sourceSwitch) {
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
	 * @param srcPort
	 *            the new src port
	 */
	@JsonProperty("src_port")
	public void setSrcPort(int srcPort) {
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
	 * @param srcVlan
	 *            the new src vlan
	 */
	@JsonProperty("src_vlan")
	public void setSrcVlan(int srcVlan) {
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
	 * @param targetSwitch
	 *            the new target switch
	 */
	@JsonProperty("target_switch")
	public void setTargetSwitch(String targetSwitch) {
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
	 * @param dstPort
	 *            the new dst port
	 */
	@JsonProperty("dst_port")
	public void setDstPort(int dstPort) {
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
	 * @param dstVlan
	 *            the new dst vlan
	 */
	@JsonProperty("dst_vlan")
	public void setDstVlan(int dstVlan) {
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
	 * @param maximumBandwidth
	 *            the new maximum bandwidth
	 */
	@JsonProperty("maximum_bandwidth")
	public void setMaximumBandwidth(int maximumBandwidth) {
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
	 * @param status
	 *            the new status
	 */
	@JsonProperty("status")
	public void setStatus(String status) {
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
	 * @param description
	 *            the new description
	 */
	@JsonProperty("description")
	public void setDescription(String description) {
		this.description = description;
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
	 * @param name
	 *            the name
	 * @param value
	 *            the value
	 */
	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
