package org.openkilda.integration.model.response;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class TopologyPath.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"port_no", "segment_latency", "seq_id", "switch_id"})
public class TopologyPath {

    /** The port no. */
    @JsonProperty("port_no")
    private Integer portNo;

    /** The segment latency. */
    @JsonProperty("segment_latency")
    private Integer segmentLatency;

    /** The seq id. */
    @JsonProperty("seq_id")
    private Integer seqId;

    /** The switch id. */
    @JsonProperty("switch_id")
    private String switchId;

    /** The additional properties. */
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Gets the port no.
     *
     * @return the port no
     */
    @JsonProperty("port_no")
    public Integer getPortNo() {
        return portNo;
    }

    /**
     * Sets the port no.
     *
     * @param portNo the new port no
     */
    @JsonProperty("port_no")
    public void setPortNo(Integer portNo) {
        this.portNo = portNo;
    }

    /**
     * Gets the segment latency.
     *
     * @return the segment latency
     */
    @JsonProperty("segment_latency")
    public Integer getSegmentLatency() {
        return segmentLatency;
    }

    /**
     * Sets the segment latency.
     *
     * @param segmentLatency the new segment latency
     */
    @JsonProperty("segment_latency")
    public void setSegmentLatency(Integer segmentLatency) {
        this.segmentLatency = segmentLatency;
    }

    /**
     * Gets the seq id.
     *
     * @return the seq id
     */
    @JsonProperty("seq_id")
    public Integer getSeqId() {
        return seqId;
    }

    /**
     * Sets the seq id.
     *
     * @param seqId the new seq id
     */
    @JsonProperty("seq_id")
    public void setSeqId(Integer seqId) {
        this.seqId = seqId;
    }

    /**
     * Gets the switch id.
     *
     * @return the switch id
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the switch id.
     *
     * @param switchId the new switch id
     */
    @JsonProperty("switch_id")
    public void setSwitchId(String switchId) {
        this.switchId = switchId;
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
     * @param name the name
     * @param value the value
     */
    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
