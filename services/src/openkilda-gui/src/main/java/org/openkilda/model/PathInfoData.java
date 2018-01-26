package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * The Class Paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"message_type", "latency_ns", "path"})
public class PathInfoData {

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("latency_ns")
    private Integer latencyNs;

    @JsonProperty("forwardPath")
    private List<PathNode> forwardPath = null;

    @JsonProperty("reversePath")
    private List<PathNode> reversePath = null;

    @JsonProperty("path")
    private List<PathNode> path = null;

    /**
     * Gets the message type.
     *
     * @return the message type
     */
    
    public String getMessageType() {
        return messageType;
    }

    /**
     * Sets the message type.
     *
     * @param messageType the new message type
     */
    
    public void setMessageType(final String messageType) {
        this.messageType = messageType;
    }

    /**
     * Gets the latency ns.
     *
     * @return the latency ns
     */
    
    public Integer getLatencyNs() {
        return latencyNs;
    }

    /**
     * Sets the latency ns.
     *
     * @param latencyNs the new latency ns
     */
    
    public void setLatencyNs(final Integer latencyNs) {
        this.latencyNs = latencyNs;
    }

    /**
     * Gets the forward path.
     *
     * @return the forward path
     */
    public List<PathNode> getForwardPath() {
        return forwardPath;
    }

    /**
     * Sets the forward path.
     *
     * @param forwardPath the new forward path
     */
    public void setForwardPath(final List<PathNode> forwardPath) {
        this.forwardPath = forwardPath;
    }

    /**
     * Gets the reverse path.
     *
     * @return the reverse path
     */
    public List<PathNode> getReversePath() {
        return reversePath;
    }

    /**
     * Sets the reverse path.
     *
     * @param reversePath the new reverse path
     */
    public void setReversePath(final List<PathNode> reversePath) {
        this.reversePath = reversePath;
    }

    /**
     * Gets the path.
     *
     * @return the path
     */
    public List<PathNode> getPath() {
        return path;
    }

    /**
     * Sets the path.
     *
     * @param path the new path
     */
    public void setPath(final List<PathNode> path) {
        this.path = path;
    }

}
