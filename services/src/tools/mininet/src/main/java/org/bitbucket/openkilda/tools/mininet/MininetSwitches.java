package org.bitbucket.openkilda.tools.mininet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.ArrayList;
import java.util.List;

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

  /**
   * Adds the switch.
   *
   * @param name the name
   * @param dpid the dpid
   * @return the MininetSwitches
   */
  public MininetSwitches addSwitch(String name, DatapathId dpid) {
    MininetSwitch sw = new MininetSwitch(name, dpid.toString(), null, null);
    switches.add(sw);
    return this;
  }
}
