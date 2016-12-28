package org.bitbucket.openkilda.tools.mininet;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "switches" })

public class MininetSwitches {

  @JsonProperty("switches")
  private List<MininetSwitch> switches = null;

  public MininetSwitches() {
    switches = new ArrayList<MininetSwitch>();
  }

  public MininetSwitches(List<MininetSwitch> switches) {
    super();
    this.switches = switches;
  }

  @JsonProperty("switches")
  public List<MininetSwitch> getSwitches() {
    return switches;
  }

  @JsonProperty("switches")
  public void setSwitches(List<MininetSwitch> switches) {
    this.switches = switches;
  }

  public MininetSwitches addSwitch(MininetSwitch sw) {
    switches.add(sw);
    return this;
  }

  public MininetSwitches addSwitch(String name, DatapathId dpid) {
    MininetSwitch sw = new MininetSwitch(name, dpid.toString(), null, null);
    switches.add(sw);
    return this;
  }
}
