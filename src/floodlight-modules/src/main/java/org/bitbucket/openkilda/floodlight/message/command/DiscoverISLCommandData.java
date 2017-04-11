package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "command",
  "destination",
  "switch_id",
  "port_no"
})

public class DiscoverISLCommandData extends CommandData {

  private static final long serialVersionUID = 1L;
  @JsonProperty("switch_id")
  private String switchId;
  @JsonProperty("port_no")
  private int portNo;

  public DiscoverISLCommandData () {
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

  public DiscoverISLCommandData withSwitchId(String switchId) {
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

  public DiscoverISLCommandData withPortNo(int portNo) {
    setPortNo(portNo);
    return this;
  }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s:%s", switchId, portNo);
    }
}
