package org.bitbucket.openkilda.floodlight.switchmanager.type;

import org.bitbucket.openkilda.floodlight.type.MessageData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "event-type",
  "switch-id"
})

public class SwitchMessageData extends MessageData {

  /**
   * 
   */
  private static final long serialVersionUID = 5975966970999152929L;
  @JsonProperty("event-type")
  private SwitchEventType eventType;

  @JsonProperty("switch-id")
  private String switchId;
  
  public SwitchMessageData() {
    // No args constructor for use in serialization
  }
  
  public SwitchMessageData(SwitchEventType eventType, String switchId) {
    this.eventType = eventType;
    this.switchId = switchId;
  }
  
  @JsonProperty("event-type")
  public SwitchEventType getEventType() {
    return eventType;
  }

  @JsonProperty("event-type")
  public void setEventType(SwitchEventType eventType) {
    this.eventType = eventType;
  }
  
  public SwitchMessageData withEventType(SwitchEventType eventType) {
    setEventType(eventType);
    return this;
  }

  @JsonProperty("switch-id")
  public String getSwitchId() {
    return switchId;
  }

  @JsonProperty("switch-id")
  public void setSwitchId(String switchId) {
    this.switchId = switchId;
  }
  
  public SwitchMessageData withSwitchId(String switchId) {
    setSwitchId(switchId);
    return this;
  }
}
