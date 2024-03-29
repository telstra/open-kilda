/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.model;

import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

@Data
public class SwitchPropertiesDto implements Serializable {
    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("supported_transit_encapsulation")
    private Set<FlowEncapsulationType> supportedTransitEncapsulation;

    @JsonProperty("multi_table")
    private boolean multiTable;

    @JsonProperty("switch_lldp")
    private boolean switchLldp;

    @JsonProperty("switch_arp")
    private boolean switchArp;

    @JsonProperty("server42_flow_rtt")
    private boolean server42FlowRtt;

    @JsonProperty("server42_port")
    private Integer server42Port;

    @JsonProperty("server42_mac_address")
    private MacAddress server42MacAddress;

    @JsonProperty("server42_vlan")
    private Integer server42Vlan;

    @JsonProperty("server42_isl_rtt")
    private RttState server42IslRtt;

    /**
     * DTO representation of {@link org.openkilda.model.SwitchProperties.RttState}.
     */
    public enum RttState {
        ENABLED, DISABLED, AUTO
    }
}
