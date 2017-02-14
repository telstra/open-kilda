package org.bitbucket.openkilda.floodlight.switchmanager.type;

import org.bitbucket.openkilda.floodlight.type.MessageData;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.floodlightcontroller.core.PortChangeType;

public class PortMessageData extends MessageData {

  /**
   * 
   */
  private static final long serialVersionUID = -5573330535458360064L;
  @JsonProperty("event-type")
  private PortChangeType eventType;
  @JsonProperty("switch-id")
  private String switchId;
  @JsonProperty("port-no")
  private int portNo;
  
  public PortMessageData() {
    // No args constructor for use in serialization
  }
  
  public PortMessageData(PortChangeType eventType, String switchId, int portNo) {
    this.eventType = eventType;
    this.switchId = switchId;
    this.portNo = portNo;
  }
  
  @JsonProperty("event-type")
  public PortChangeType getEventType() {
    return eventType;
  }

  @JsonProperty("event-type")
  public void setEventType(PortChangeType eventType) {
    this.eventType = eventType;
  }
  
  public PortMessageData withEventType(PortChangeType eventType) {
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
  
  public PortMessageData withSwitchId(String switchId) {
    setSwitchId(switchId);
    return this;
  }
  
  @JsonProperty("port-no")
  public int getPortNo() {
    return portNo;
  }
  
  @JsonProperty("port-no")
  public void setPortNo(int portNo) {
    this.portNo = portNo;
  }
  
  public PortMessageData withPortNo(int portNo) {
    setPortNo(portNo);
    return this;
  }
}
