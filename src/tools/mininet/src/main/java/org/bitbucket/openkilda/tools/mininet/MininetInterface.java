package org.bitbucket.openkilda.tools.mininet;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MininetInterface {
  @JsonProperty("status")
  private String status;
  @JsonProperty("mac")
  private String mac;
  @JsonProperty("name")
  private String name;

  public MininetInterface() {

  }

  public MininetInterface(String status, String mac, String name) {
    this.status = status;
    this.mac = mac;
    this.name = name;
  }

  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(String status) {
    this.status = status;
  }

  @JsonProperty("mac")
  public String getMac() {
    return mac;
  }

  @JsonProperty("mac")
  public void setMac(String mac) {
    this.mac = mac;
  }

  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }
}
