package org.bitbucket.openkilda.floodlight.message.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import net.floodlightcontroller.core.PortChangeType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "message_type",
  "switch_id",
  "port_no",
  "max_capacity",
  "state"
})

public class PortInfoData extends InfoData {

  private static final long serialVersionUID = 1L;
  
  @JsonProperty("switch_id")
  private String switchId;
  @JsonProperty("port_no")
  private int portNo;
  @JsonProperty("max_capacity")
  private int maxCapacity;
  @JsonProperty("state")
  private PortChangeType state;
  
  public PortInfoData() {
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
  
  public PortInfoData withSwitchId(String switchId) {
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
  
  public PortInfoData withPortNo(int portNo) {
    setPortNo(portNo);
    return this;
  }

  @JsonProperty("max_capacity")
  public int getMaxCapacity() {
    return maxCapacity;
  }

  @JsonProperty("max_capacity")
  public void setMaxCapacity(int maxCapacity) {
    this.maxCapacity = maxCapacity;
  }
  
  public PortInfoData withMaxCapacity(int capacity) {
    setMaxCapacity(capacity);
    return this;
  }

  @JsonProperty("state")
  public PortChangeType getState() {
    return state;
  }

  @JsonProperty("state")
  public void setState(PortChangeType state) {
    this.state = state;
  }
  
  public PortInfoData withState(PortChangeType state) {
    setState(state);
    return this;
  }
}
