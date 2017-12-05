package org.openkilda.ws.response;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Switchrelation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "src_port", "latency", "source_switch",
		"available_bandwidth", "dst_port", "target_switch", "speed", "state",
		"flows" })
public class Switchrelation implements Serializable {

	/** The src port. */
	@JsonProperty("src_port")
	private int srcPort;

	/** The latency. */
	@JsonProperty("latency")
	private int latency;

	/** The source switch. */
	@JsonProperty("source_switch")
	private String sourceSwitch;

	/** The available bandwidth. */
	@JsonProperty("available_bandwidth")
	private int availableBandwidth;

	/** The dst port. */
	@JsonProperty("dst_port")
	private int dstPort;

	/** The target switch. */
	@JsonProperty("target_switch")
	private String targetSwitch;

	/** The speed. */
	@JsonProperty("speed")
	private int speed;

	/** The state. */
	@JsonProperty("state")
	private String state;

	/** The flows. */
	@JsonProperty("flows")
	private List<Flow> flows = null;

	/** The additional properties. */
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	/** The Constant serialVersionUID. */
	private final static long serialVersionUID = 5556955805857357959L;

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
	 * Gets the latency.
	 *
	 * @return the latency
	 */
	@JsonProperty("latency")
	public int getLatency() {
		return latency;
	}

	/**
	 * Sets the latency.
	 *
	 * @param latency
	 *            the new latency
	 */
	@JsonProperty("latency")
	public void setLatency(int latency) {
		this.latency = latency;
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
	 * Gets the available bandwidth.
	 *
	 * @return the available bandwidth
	 */
	@JsonProperty("available_bandwidth")
	public int getAvailableBandwidth() {
		return availableBandwidth;
	}

	/**
	 * Sets the available bandwidth.
	 *
	 * @param availableBandwidth
	 *            the new available bandwidth
	 */
	@JsonProperty("available_bandwidth")
	public void setAvailableBandwidth(int availableBandwidth) {
		this.availableBandwidth = availableBandwidth;
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
	 * Gets the speed.
	 *
	 * @return the speed
	 */
	@JsonProperty("speed")
	public int getSpeed() {
		return speed;
	}

	/**
	 * Sets the speed.
	 *
	 * @param speed
	 *            the new speed
	 */
	@JsonProperty("speed")
	public void setSpeed(int speed) {
		this.speed = speed;
	}

	/**
	 * Gets the state.
	 *
	 * @return the state
	 */
	@JsonProperty("state")
	public String getState() {
		return state;
	}

	/**
	 * Sets the state.
	 *
	 * @param state
	 *            the new state
	 */
	@JsonProperty("state")
	public void setState(String state) {
		this.state = state;
	}

	/**
	 * Gets the flows.
	 *
	 * @return the flows
	 */
	@JsonProperty("flows")
	public List<Flow> getFlows() {
		return flows;
	}

	/**
	 * Sets the flows.
	 *
	 * @param flows
	 *            the new flows
	 */
	@JsonProperty("flows")
	public void setFlows(List<Flow> flows) {
		this.flows = flows;
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
