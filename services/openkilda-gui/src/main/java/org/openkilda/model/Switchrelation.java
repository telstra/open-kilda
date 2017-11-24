package org.openkilda.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Switchrelation.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"src_port", "latency", "source_switch", "available_bandwidth", "dst_port",
        "target_switch", "speed", "state"})
public class Switchrelation implements Serializable {

    /** The src port. */
    @JsonProperty("src_port")
    private int srcPort;

    /** The latency. */
    @JsonProperty("latency")
    private int latency;

    /** The src switch. */
    @JsonProperty("source_switch")
    private String srcSwitch;

    /** The available bandwidth. */
    @JsonProperty("available_bandwidth")
    private int availableBandwidth;

    /** The dst port. */
    @JsonProperty("dst_port")
    private int dstPort;

    /** The dst switch. */
    @JsonProperty("target_switch")
    private String dstSwitch;

    /** The speed. */
    @JsonProperty("speed")
    private int speed;

    /** The state. */
    @JsonProperty("state")
    private String state;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 8274573430947748236L;

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
     * @param latency the new latency
     */
    @JsonProperty("latency")
    public void setLatency(int latency) {
        this.latency = latency;
    }

    /**
     * Gets the src switch.
     *
     * @return the src switch
     */
    @JsonProperty("source_switch")
    public String getSrcSwitch() {
        return srcSwitch;
    }

    /**
     * Sets the src switch.
     *
     * @param srcSwitch the new src switch
     */
    @JsonProperty("source_switch")
    public void setSrcSwitch(String srcSwitch) {
        this.srcSwitch = srcSwitch;
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
     * @param availableBandwidth the new available bandwidth
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
     * @param dstPort the new dst port
     */
    @JsonProperty("dst_port")
    public void setDstPort(int dstPort) {
        this.dstPort = dstPort;
    }

    /**
     * Gets the dst switch.
     *
     * @return the dst switch
     */
    @JsonProperty("target_switch")
    public String getDstSwitch() {
        return dstSwitch;
    }

    /**
     * Sets the dst switch.
     *
     * @param dstSwitch the new dst switch
     */
    @JsonProperty("target_switch")
    public void setDstSwitch(String dstSwitch) {
        this.dstSwitch = dstSwitch;
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
     * @param speed the new speed
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
     * @param state the new state
     */
    @JsonProperty("state")
    public void setState(String state) {
        this.state = state;
    }

}
