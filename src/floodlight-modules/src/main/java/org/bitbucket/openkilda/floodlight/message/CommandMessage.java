package org.bitbucket.openkilda.floodlight.message;

import org.bitbucket.openkilda.floodlight.message.command.CommandData;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CommandMessage extends Message {

  private static final long serialVersionUID = 1L;
  @JsonProperty("data")
  private CommandData data;
  
  public CommandMessage() {
  }
  
  @JsonProperty("data")
  public CommandData getData() {
    return data;
  }
  
  @JsonProperty("data")
  public void setData(CommandData data) {
    this.data = data;
  }
  
  public CommandMessage withData(CommandData data) {
    setData(data);
    return this;
  }
}
