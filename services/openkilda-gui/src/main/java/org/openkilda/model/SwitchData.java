package org.openkilda.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The Class SwitchData.
 * 
 * @author Gaurav Chugh
 */
@SuppressWarnings("deprecation")
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwitchData implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 7335628340408278121L;

    /** The controller. */
    @JsonProperty("controller")
    private String controller;

    /** The hostname. */
    @JsonProperty("hostname")
    private String hostname;

    /** The source address. */
    @JsonProperty("source-address")
    private String sourceAddress;

    /** The target addres. */
    @JsonProperty("des-address")
    private String targetAddres;

    /** The source name. */
    @JsonProperty("source")
    private String sourceName;

    /** The target name. */
    @JsonProperty("target")
    private String targetName;

    /** The description. */
    @JsonProperty("description")
    private String description;

    /** The source state. */
    @JsonProperty("source-state")
    private String sourceState;

    /** The target state. */
    @JsonProperty("target-state")
    private String targetState;

    /** The source port. */
    @JsonProperty("src-port")
    private Integer sourcePort;

    /** The target port. */
    @JsonProperty("des-port")
    private Integer targetPort;

    /** The source switch. */
    @JsonProperty("src-switch")
    private String sourceSwitch;

    /** The target switch. */
    @JsonProperty("des-switch")
    private String targetSwitch;

    /** The available_bandwidth. */
    @JsonProperty("available_bandwidth")
    private Integer available_bandwidth;

    /** The speed. */
    @JsonProperty("speed")
    private Integer speed;

    /** The latency. */
    @JsonProperty("latency")
    private Integer latency;

    /**
     * Gets the controller.
     *
     * @return the controller
     */
    public String getController() {
        return controller;
    }

    /**
     * Gets the hostname.
     *
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Sets the hostname.
     *
     * @param hostname the new hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Gets the source address.
     *
     * @return the source address
     */
    public String getSourceAddress() {
        return sourceAddress;
    }

    /**
     * Sets the source address.
     *
     * @param sourceAddress the new source address
     */
    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    /**
     * Gets the target addres.
     *
     * @return the target addres
     */
    public String getTargetAddres() {
        return targetAddres;
    }

    /**
     * Sets the target addres.
     *
     * @param targetAddres the new target addres
     */
    public void setTargetAddres(String targetAddres) {
        this.targetAddres = targetAddres;
    }

    /**
     * Gets the source name.
     *
     * @return the source name
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Sets the source name.
     *
     * @param sourceName the new source name
     */
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * Gets the target name.
     *
     * @return the target name
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Sets the target name.
     *
     * @param targetName the new target name
     */
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the source state.
     *
     * @return the source state
     */
    public String getSourceState() {
        return sourceState;
    }

    /**
     * Sets the source state.
     *
     * @param sourceState the new source state
     */
    public void setSourceState(String sourceState) {
        this.sourceState = sourceState;
    }

    /**
     * Gets the target state.
     *
     * @return the target state
     */
    public String getTargetState() {
        return targetState;
    }

    /**
     * Sets the target state.
     *
     * @param targetState the new target state
     */
    public void setTargetState(String targetState) {
        this.targetState = targetState;
    }

    /**
     * Gets the source port.
     *
     * @return the source port
     */
    public Integer getSourcePort() {
        return sourcePort;
    }

    /**
     * Sets the source port.
     *
     * @param sourcePort the new source port
     */
    public void setSourcePort(Integer sourcePort) {
        this.sourcePort = sourcePort;
    }

    /**
     * Gets the target port.
     *
     * @return the target port
     */
    public Integer getTargetPort() {
        return targetPort;
    }

    /**
     * Sets the target port.
     *
     * @param targetPort the new target port
     */
    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    /**
     * Gets the source switch.
     *
     * @return the source switch
     */
    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Sets the source switch.
     *
     * @param sourceSwitch the new source switch
     */
    public void setSourceSwitch(String sourceSwitch) {
        this.sourceSwitch = sourceSwitch;
    }

    /**
     * Gets the target switch.
     *
     * @return the target switch
     */
    public String getTargetSwitch() {
        return targetSwitch;
    }

    /**
     * Sets the target switch.
     *
     * @param targetSwitch the new target switch
     */
    public void setTargetSwitch(String targetSwitch) {
        this.targetSwitch = targetSwitch;
    }

    /**
     * Gets the available_bandwidth.
     *
     * @return the available_bandwidth
     */
    public Integer getAvailable_bandwidth() {
        return available_bandwidth;
    }

    /**
     * Sets the available_bandwidth.
     *
     * @param available_bandwidth the new available_bandwidth
     */
    public void setAvailable_bandwidth(Integer available_bandwidth) {
        this.available_bandwidth = available_bandwidth;
    }

    /**
     * Gets the speed.
     *
     * @return the speed
     */
    public Integer getSpeed() {
        return speed;
    }

    /**
     * Sets the speed.
     *
     * @param speed the new speed
     */
    public void setSpeed(Integer speed) {
        this.speed = speed;
    }

    /**
     * Gets the latency.
     *
     * @return the latency
     */
    public Integer getLatency() {
        return latency;
    }

    /**
     * Sets the latency.
     *
     * @param latency the new latency
     */
    public void setLatency(Integer latency) {
        this.latency = latency;
    }

    /**
     * Sets the controller.
     *
     * @param controller the new controller
     */
    public void setController(String controller) {
        this.controller = controller;
    }



}
