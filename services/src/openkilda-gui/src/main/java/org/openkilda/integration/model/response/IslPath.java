package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switch_id", "port_no", "seq_id", "segment_latency"})
public class IslPath {

    @JsonProperty("switch_id")
    private String switchId;
    @JsonProperty("port_no")
    private Integer portNo;
    @JsonProperty("seq_id")
    private Integer seqId;
    @JsonProperty("segment_latency")
    private Integer segmentLatency;

    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    public Integer getPortNo() {
        return portNo;
    }

    public void setPortNo(final Integer portNo) {
        this.portNo = portNo;
    }

    public Integer getSeqId() {
        return seqId;
    }

    public void setSeqId(final Integer seqId) {
        this.seqId = seqId;
    }

    public Integer getSegmentLatency() {
        return segmentLatency;
    }

    public void setSegmentLatency(final Integer segmentLatency) {
        this.segmentLatency = segmentLatency;
    }

    @Override
    public String toString() {
        return "IslPath [switchId=" + switchId + ", portNo=" + portNo + ", seqId=" + seqId
                + ", segmentLatency=" + segmentLatency + "]";
    }


}
