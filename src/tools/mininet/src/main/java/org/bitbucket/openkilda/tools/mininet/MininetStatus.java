package org.bitbucket.openkilda.tools.mininet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "status" })
public class MininetStatus {

  @JsonProperty("status")
  private String status;

  public MininetStatus() {
    // Needed for Jackson.
  }

  public MininetStatus(String status) {
    super();
    this.status = status;
  }

  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(String status) {
    this.status = status;
  }
  
  public boolean isConnected() {
    return "ok".equals(status);
  }

}
