package org.bitbucket.openkilda.floodlight.message;

import org.bitbucket.openkilda.floodlight.message.info.InfoData;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InfoMessage extends Message {

  private static final long serialVersionUID = 1L;
  @JsonProperty("data")
  private InfoData data;
  
  public InfoMessage() {
  }

  @JsonProperty("data")
  public InfoData getData() {
    return data;
  }

  @JsonProperty("data")
  public void setData(InfoData data) {
    this.data = data;
  }
  
  public InfoMessage withData(InfoData data) {
    setData(data);
    return this;
  }
  
  
}
