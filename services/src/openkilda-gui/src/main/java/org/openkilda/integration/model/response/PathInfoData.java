package org.openkilda.integration.model.response;

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

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(final String messageType) {
        this.messageType = messageType;
    }

    public Integer getLatencyNs() {
        return latencyNs;
    }

    public void setLatencyNs(final Integer latencyNs) {
        this.latencyNs = latencyNs;
    }

    public List<PathNode> getForwardPath() {
        return forwardPath;
    }

    public void setForwardPath(final List<PathNode> forwardPath) {
        this.forwardPath = forwardPath;
    }

    public List<PathNode> getReversePath() {
        return reversePath;
    }

    public void setReversePath(final List<PathNode> reversePath) {
        this.reversePath = reversePath;
    }

    public List<PathNode> getPath() {
        return path;
    }

    public void setPath(final List<PathNode> path) {
        this.path = path;
    }
}
