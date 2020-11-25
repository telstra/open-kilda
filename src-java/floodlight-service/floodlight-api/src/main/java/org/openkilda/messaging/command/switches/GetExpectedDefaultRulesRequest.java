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
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class GetExpectedDefaultRulesRequest extends CommandData {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("multi_table")
    private boolean multiTable;

    @JsonProperty("switch_lldp")
    private boolean switchLldp;

    @JsonProperty("switch_arp")
    private boolean switchArp;

    @JsonProperty("server42_flow_rtt_feature_toggle")
    private boolean server42FlowRttFeatureToggle;

    @JsonProperty("server42_flow_rtt_switch_property")
    private boolean server42FlowRttSwitchProperty;

    @JsonProperty("server42_port")
    private Integer server42Port;

    @JsonProperty("server42_vlan")
    private Integer server42Vlan;

    @JsonProperty("server42_mac_address")
    private MacAddress server42MacAddress;

    @JsonProperty("isl_ports")
    private  List<Integer> islPorts;

    @JsonProperty("flow_ports")
    private List<Integer> flowPorts;

    @JsonProperty("flow_lldp_ports")
    private Set<Integer> flowLldpPorts;

    @JsonProperty("flow_arp_ports")
    private Set<Integer> flowArpPorts;

    @JsonProperty("server42_flow_rtt_ports")
    private Set<Integer> server42FlowRttPorts;

    @Builder
    public GetExpectedDefaultRulesRequest(@JsonProperty("switch_id") SwitchId switchId,
                                          @JsonProperty("multi_table") boolean multiTable,
                                          @JsonProperty("switch_lldp") boolean switchLldp,
                                          @JsonProperty("switch_arp") boolean switchArp,
                                          @JsonProperty("server42_flow_rtt_feature_toggle")
                                                      boolean server42FlowRttFeatureToggle,
                                          @JsonProperty("server42_flow_rtt_switch_property")
                                                      boolean server42FlowRttSwitchProperty,
                                          @JsonProperty("server42_port") Integer server42Port,
                                          @JsonProperty("server42_vlan") Integer server42Vlan,
                                          @JsonProperty("server42_mac_address") MacAddress server42MacAddress,
                                          @JsonProperty("isl_ports") @Singular List<Integer> islPorts,
                                          @JsonProperty("flow_ports") @Singular List<Integer> flowPorts,
                                          @JsonProperty("flow_lldp_ports") @Singular Set<Integer> flowLldpPorts,
                                          @JsonProperty("flow_arp_ports") @Singular Set<Integer> flowArpPorts,
                                          @JsonProperty("server42_flow_rtt_ports") @Singular
                                                  Set<Integer> server42FlowRttPorts) {
        this.switchId = switchId;
        this.multiTable = multiTable;
        this.switchLldp = switchLldp;
        this.switchArp = switchArp;
        this.server42FlowRttFeatureToggle = server42FlowRttFeatureToggle;
        this.server42FlowRttSwitchProperty = server42FlowRttSwitchProperty;
        this.server42Port = server42Port;
        this.server42Vlan = server42Vlan;
        this.server42MacAddress = server42MacAddress;
        this.islPorts = islPorts;
        this.flowPorts = flowPorts;
        this.flowLldpPorts = flowLldpPorts;
        this.flowArpPorts = flowArpPorts;
        this.server42FlowRttPorts = server42FlowRttPorts;
    }
}
