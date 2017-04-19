package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "command",
  "destination",
  "source_switch_id",
  "source_port_no",
  "destination_switch_id"
})

public class DiscoverPathCommandData extends CommandData {

  private static final long serialVersionUID = 1L;
  @JsonProperty("source_switch_id")
  private String srcSwitchId;
  @JsonProperty("source_port_no")
  private int srcPortNo;
  @JsonProperty("destination_switch_id")
  private String dstSwitchId;
  
  public DiscoverPathCommandData() {
    // no args init for serializer
  }
  
  @JsonProperty("source_switch_id")
  public String getSrcSwitchId() {
    return srcSwitchId;
  }
  
  @JsonProperty("source_switch_id")
  public void setSrcSwitchId(String switchId) {
    this.srcSwitchId = switchId;
  }
  
  public DiscoverPathCommandData withSrcSwitchId(String switchId) {
    setSrcSwitchId(switchId);
    return this;
  }
  
  @JsonProperty("source_port_no")
  public int getSrcPortNo() {
    return srcPortNo;
  }
  
  @JsonProperty("source_port_no")
  public void setSrcPortNo(int portNo) {
    this.srcPortNo = portNo;
  }
  
  public DiscoverPathCommandData withSrcPortNo(int portNo) {
    setSrcPortNo(portNo);
    return this;
  }
  
  @JsonProperty("destination_switch_id")
  public String getDstSwitchId() {
    return dstSwitchId;
  }
  
  @JsonProperty("destination_switch_id")
  public void setDstSwitchId(String switchId) {
    this.dstSwitchId = switchId;
  }
  
  public DiscoverPathCommandData withDstSwitchId(String switchId) {
    setDstSwitchId(switchId);
    return this;
  }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s:%s -> %s", srcSwitchId, srcPortNo, dstSwitchId);
    }
}
