package org.bitbucket.openkilda.floodlight.message.info;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "switch_id",
  "port_no",
  "seq_id",
  "segment_latency"
})

public class PathNode implements Serializable {

  private static final long serialVersionUID = 1L;
  @JsonProperty("switch_id")
  private String switchId;
  @JsonProperty("port_no")
  private int portNo;
  @JsonProperty("seq_id")
  private int seqId;
  @JsonInclude(JsonInclude.Include.NON_DEFAULT) // Needed to exclude when not set
  @JsonProperty("segment_latency")
  private long segmentLatency ;
  
  public PathNode() {
    // no args init for serializer
  }
  
  @JsonProperty("switch_id")
  public String getSwitchId() {
    return switchId;
  }
  
  @JsonProperty("switch_id")
  public void setSwitchId(String switchId) {
    this.switchId = switchId;
  }
  
  public PathNode withSwitchId(String switchId) {
    setSwitchId(switchId);
    return this;
  }
  
  @JsonProperty("port_no")
  public int getPortNo() {
    return portNo;
  }
  
  @JsonProperty("port_no")
  public void setPortNo(int portNo) {
    this.portNo = portNo;
  }
  
  public PathNode withPortNo(int portNo) {
    setPortNo(portNo);
    return this;
  }
  @JsonProperty("seq_id")
  public int getSeqId() {
    return seqId;
  }
  
  @JsonProperty("seq_id")
  public void setSeqId(int seqId) {
    this.seqId = seqId;
  }
  
  public PathNode withSeqId(int seqId) {
    setSeqId(seqId);
    return this;
  }
  
  @JsonProperty("segment_latency")
  public long getSegLatency() {
    return segmentLatency;
  }
  
  @JsonProperty("segment_latency")
  public void setSegLatency(long latency) {
    this.segmentLatency = latency;
  }
  
  public PathNode withSegLatency(long l) {
    setSegLatency(l);
    return this;
  }
}
