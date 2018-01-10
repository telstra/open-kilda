package org.openkilda.integration.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"message_type", "latency_ns", "path"})
public class PathInfoData {

    /** The message type. */
    @JsonProperty("message_type")
    private String messageType;

    /** The latency ns. */
    @JsonProperty("latency_ns")
    private Integer latencyNs;

    /** The forwardPath. */
    @JsonProperty("forwardPath")
    private List<PathNode> forwardPath = null;

    /** The reversePath. */
    @JsonProperty("reversePath")
    private List<PathNode> reversePath = null;

    /** The reversePath. */
    @JsonProperty("path")
    private List<PathNode> path = null;

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
     * Gets the latency ns.
     *
     * @return the latency ns
     */
    @JsonProperty("latency_ns")
    public Integer getLatencyNs() {
        return latencyNs;
    }

    /**
     * Sets the latency ns.
     *
     * @param latencyNs the new latency ns
     */
    @JsonProperty("latency_ns")
    public void setLatencyNs(Integer latencyNs) {
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
    public void setForwardPath(List<PathNode> forwardPath) {
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
    public void setReversePath(List<PathNode> reversePath) {
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
    public void setPath(List<PathNode> path) {
        this.path = path;
    }

}
