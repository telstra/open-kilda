
package org.openkilda.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Path.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "port_no",
    "segment_latency",
    "seq_id",
    "switch_id"
})
public class Path implements Serializable
{

    /** The port no. */
    @JsonProperty("port_no")
    private int portNo;
    
    /** The segment latency. */
    @JsonProperty("segment_latency")
    private int segmentLatency;
    
    /** The seq id. */
    @JsonProperty("seq_id")
    private int seqId;
    
    /** The switch id. */
    @JsonProperty("switch_id")
    private String switchId;
    
    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -4515006227265225751L;

    /**
     * Gets the port no.
     *
     * @return the port no
     */
    @JsonProperty("port_no")
    public int getPortNo() {
        return portNo;
    }

    /**
     * Sets the port no.
     *
     * @param portNo the new port no
     */
    @JsonProperty("port_no")
    public void setPortNo(int portNo) {
        this.portNo = portNo;
    }

    /**
     * Gets the segment latency.
     *
     * @return the segment latency
     */
    @JsonProperty("segment_latency")
    public int getSegmentLatency() {
        return segmentLatency;
    }

    /**
     * Sets the segment latency.
     *
     * @param segmentLatency the new segment latency
     */
    @JsonProperty("segment_latency")
    public void setSegmentLatency(int segmentLatency) {
        this.segmentLatency = segmentLatency;
    }

    /**
     * Gets the seq id.
     *
     * @return the seq id
     */
    @JsonProperty("seq_id")
    public int getSeqId() {
        return seqId;
    }

    /**
     * Sets the seq id.
     *
     * @param seqId the new seq id
     */
    @JsonProperty("seq_id")
    public void setSeqId(int seqId) {
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

}
