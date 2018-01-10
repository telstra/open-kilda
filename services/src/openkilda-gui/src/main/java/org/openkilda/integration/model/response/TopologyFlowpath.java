package org.openkilda.integration.model.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;



@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"latency_ns", "message_type", "path"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopologyFlowpath {

    @JsonProperty("latency_ns")
    private Integer latencyNs;
    @JsonProperty("message_type")
    private String messageType;
    @JsonProperty("path")
    private List<TopologyPath> path = null;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("latency_ns")
    public Integer getLatencyNs() {
        return latencyNs;
    }

    @JsonProperty("latency_ns")
    public void setLatencyNs(Integer latencyNs) {
        this.latencyNs = latencyNs;
    }

    @JsonProperty("message_type")
    public String getMessageType() {
        return messageType;
    }

    @JsonProperty("message_type")
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    @JsonProperty("path")
    public List<TopologyPath> getPath() {
        return path;
    }

    /**
     * Sets the path.
     *
     * @param path the new path
     */
    @JsonProperty("path")
    public void setPath(List<TopologyPath> path) {
        this.path = path;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
