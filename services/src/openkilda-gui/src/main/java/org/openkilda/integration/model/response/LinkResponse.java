package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class LinkResponse.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"available_bandwidth", "latency_ns", "message_type", "path", "speed", "state"})
public class LinkResponse implements Serializable {

    /** The available bandwidth. */
    @JsonProperty("available_bandwidth")
    private int availableBandwidth;

    /** The latency ns. */
    @JsonProperty("latency_ns")
    private int latencyNs;

    /** The message type. */
    @JsonProperty("message_type")
    private String messageType;

    /** The path. */
    @JsonProperty("path")
    private List<PathNode> path = null;

    /** The speed. */
    @JsonProperty("speed")
    private int speed;

    /** The state. */
    @JsonProperty("state")
    private String state;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -4203643241516861695L;

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
     * Gets the latency ns.
     *
     * @return the latency ns
     */
    @JsonProperty("latency_ns")
    public int getLatencyNs() {
        return latencyNs;
    }

    /**
     * Sets the latency ns.
     *
     * @param latencyNs the new latency ns
     */
    @JsonProperty("latency_ns")
    public void setLatencyNs(int latencyNs) {
        this.latencyNs = latencyNs;
    }

    /**
     * Gets the message type.
     *
     * @return the message type
     */
    @JsonProperty("message_type")
    public String getMessageType() {
        return messageType;
    }

    /**
     * Sets the message type.
     *
     * @param messageType the new message type
     */
    @JsonProperty("message_type")
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /**
     * Gets the path.
     *
     * @return the path
     */
    @JsonProperty("path")
    public List<PathNode> getPath() {
        return path;
    }

    /**
     * Sets the path.
     *
     * @param path the new path
     */
    @JsonProperty("path")
    public void setPath(List<PathNode> path) {
        this.path = path;
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
