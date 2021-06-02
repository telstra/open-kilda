/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.model.MirrorConfig;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Class represents flow through one switch installation info.
 * Input and output vlan ids are optional, because flow could be untagged on ingoing or outgoing side.
 * Output action depends on flow input and output vlan presence.
 * Bandwidth and meter id are used for flow throughput limitation.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "input_port",
        "output_port",
        "input_vlan_id",
        "output_vlan_id",
        "output_vlan_type",
        "bandwidth",
        "meter_id"})
@EqualsAndHashCode(callSuper = true)
public class InstallOneSwitchMirrorFlow extends InstallOneSwitchFlow {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id id of the flow
     * @param cookie flow cookie
     * @param switchId switch ID for flow installation
     * @param inputPort input port of the flow
     * @param outputPort output port of the flow
     * @param inputVlanId input vlan id value
     * @param inputInnerVlanId input inner vlan id
     * @param outputVlanId output vlan id value
     * @param outputInnerVlanId output inner vlan id
     * @param outputVlanType output vlan tag action
     * @param bandwidth flow bandwidth
     * @param meterId source meter id
     * @param enableLldp install LLDP shared rule if True
     * @param enableArp install ARP shared rule if True
     * @param mirrorConfig   flow mirror config
     * @throws IllegalArgumentException if any of arguments is null
     */
    @JsonCreator
    public InstallOneSwitchMirrorFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                                      @JsonProperty(FLOW_ID) final String id,
                                      @JsonProperty("cookie") final Long cookie,
                                      @JsonProperty("switch_id") final SwitchId switchId,
                                      @JsonProperty("input_port") final Integer inputPort,
                                      @JsonProperty("output_port") final Integer outputPort,
                                      @JsonProperty("input_vlan_id") final Integer inputVlanId,
                                      @JsonProperty("input_inner_vlan_id") Integer inputInnerVlanId,
                                      @JsonProperty("output_vlan_id") final Integer outputVlanId,
                                      @JsonProperty("output_inner_vlan_id") Integer outputInnerVlanId,
                                      @JsonProperty("output_vlan_type") final OutputVlanType outputVlanType,
                                      @JsonProperty("bandwidth") final Long bandwidth,
                                      @JsonProperty("meter_id") final Long meterId,
                                      @JsonProperty("multi_table") final boolean multiTable,
                                      @JsonProperty("enable_lldp") final boolean enableLldp,
                                      @JsonProperty("enable_arp") final boolean enableArp,
                                      @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, inputVlanId, inputInnerVlanId, outputVlanId,
                outputInnerVlanId, outputVlanType, bandwidth, meterId, multiTable, enableLldp, enableArp, mirrorConfig);
    }
}
