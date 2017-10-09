/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.tools.mininet;

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
