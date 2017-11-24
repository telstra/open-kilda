package org.openkilda.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class SwitchRelationModel.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"src_port", "latency", "src_switch", "available_bandwidth", "dst_port",
        "dst_switch", "speed"})
public class SwitchRelationModel implements Serializable {

    /** The src port. */
    @JsonProperty("src_port")
    private int srcPort;

    /** The latency. */
    @JsonProperty("latency")
    private int latency;

    /** The src switch. */
    @JsonProperty("src_switch")
    private String srcSwitch;

    /** The available bandwidth. */
    @JsonProperty("available_bandwidth")
    private int availableBandwidth;

    /** The dst port. */
    @JsonProperty("dst_port")
    private int dstPort;

    /** The dst switch. */
    @JsonProperty("dst_switch")
    private String dstSwitch;

    /** The speed. */
    @JsonProperty("speed")
    private int speed;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 3387999729712044536L;

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

}
