package org.bitbucket.openkilda.floodlight.message.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "message_type",
  "switch_id",
  "state"
})

public class SwitchInfoData extends InfoData {

  private static final long serialVersionUID = 1L;

  @JsonProperty("switch_id")
  private String switchId;
  @JsonProperty("state")
  private SwitchEventType state;
  
  public enum SwitchEventType {
    ACTIVATED,
    ADDED,
    CHANGED,
    DEACTIVATED,
    REMOVED
  }
  
  public SwitchInfoData() {
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
  
  public SwitchInfoData withSwitchId(String switchId) {
    setSwitchId(switchId);
    return this;
  }
  
  @JsonProperty("state")
  public SwitchEventType getState() {
    return state;
  }
  
  @JsonProperty("state")
  public void setState(SwitchEventType state) {
    this.state = state;
  }
  
  public SwitchInfoData withState(SwitchEventType state) {
    setState(state);
    return this;
  }
}
