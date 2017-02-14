package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "command",
  "destination",
  "switch_id"
})

public class DefaultFlowsCommandData extends CommandData {

  private static final long serialVersionUID = 1L;
  @JsonProperty("switch_id")
  private String switchId;
  
  public DefaultFlowsCommandData() {
    // no arg init for serializer
  }
  
  @JsonProperty("switch_id")
  public String getSwitchId() {
    return switchId;
  }
  
  @JsonProperty("switch_id")
  public void setSwitchId(String switchId) {
    this.switchId = switchId;
  }
  
  public DefaultFlowsCommandData withSwitchId(String switchId) {
    setSwitchId(switchId);
    return this;
  }
}
