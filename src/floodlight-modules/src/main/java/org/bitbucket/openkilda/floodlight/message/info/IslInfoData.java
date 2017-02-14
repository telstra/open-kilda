package org.bitbucket.openkilda.floodlight.message.info;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "message-type",
  "latency_ns",
  "path"
})

public class IslInfoData extends InfoData {

  private static final long serialVersionUID = 1L;
  @JsonProperty("latency_ns")
  private long latency;
  @JsonProperty("path")
  private List<PathNode> path;

  public IslInfoData() {
    // no args init for serializer
  }
  
  @JsonProperty("latency_ns")
  public long getLatency() {
    return latency;
  }
  
  @JsonProperty("latency_ns")
  public void setLatency(long latency) {
    this.latency = latency;
  }
  
  public IslInfoData withLatency(long latency) {
    setLatency(latency);
    return this;
  }
  
  @JsonProperty("path")
  public List<PathNode> getPath() {
    return path;
  }
  
  @JsonProperty("path")
  public void setPath(List<PathNode> path) {
    this.path = path;
  }
  
  public IslInfoData withPath(List<PathNode> path) {
    setPath(path);
    return this;
  }
}
