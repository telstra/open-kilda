/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.command.switches;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@Builder
public class GetExpectedDefaultRulesRequest extends CommandData {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("multi_table")
    private boolean multiTable;

    @JsonProperty("switch_lldp")
    private boolean switchLldp;

    @JsonProperty("isl_ports")
    private  List<Integer> islPorts;

    @JsonProperty("flow_ports")
    private List<Integer> flowPorts;

    @JsonProperty("flow_lldp_ports")
    private Set<Integer> flowLldpPorts;

    public GetExpectedDefaultRulesRequest(@JsonProperty("switch_id") SwitchId switchId,
                                          @JsonProperty("multi_table") boolean multiTable,
                                          @JsonProperty("switch_lldp") boolean switchLldp,
                                          @JsonProperty("isl_ports") List<Integer> islPorts,
                                          @JsonProperty("flow_ports") List<Integer> flowPorts,
                                          @JsonProperty("flow_lldp_ports") Set<Integer> flowLldpPorts) {
        this.switchId = switchId;
        this.multiTable = multiTable;
        this.switchLldp = switchLldp;
        this.islPorts = islPorts;
        this.flowPorts = flowPorts;
        this.flowLldpPorts = flowLldpPorts;
    }
}
