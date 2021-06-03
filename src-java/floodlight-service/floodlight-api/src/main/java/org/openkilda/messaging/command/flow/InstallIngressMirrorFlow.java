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

import org.openkilda.model.FlowEncapsulationType;
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

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "input_port",
        "output_port",
        "input_vlan_id",
        "transit_encapsulation_id",
        "transit_encapsulation_type",
        "output_vlan_type",
        "bandwidth",
        "meter_id"})
@EqualsAndHashCode(callSuper = true)
public class InstallIngressMirrorFlow extends InstallIngressFlow {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Instance constructor.
     *
     * @param transactionId  transaction id
     * @param id             id of the flow
     * @param cookie         flow cookie
     * @param switchId       switch ID for flow installation
     * @param inputPort      input port of the flow
     * @param outputPort     output port of the flow
     * @param inputVlanId    input vlan id value
     * @param inputInnerVlanId input inner vlan id value
     * @param transitEncapsulationId  transit encapsulation id value
     * @param transitEncapsulationType  transit encapsulation type value
     * @param outputVlanType output vlan type action
     * @param bandwidth      flow bandwidth
     * @param meterId        flow meter id
     * @param egressSwitchId id of the ingress switch
     * @param multiTable     multitable flag
     * @param enableLldp lldp flag. Packets will be send to LLDP rule if True.
     * @param mirrorConfig   flow mirror config
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public InstallIngressMirrorFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                                    @JsonProperty(FLOW_ID) final String id,
                                    @JsonProperty("cookie") final Long cookie,
                                    @JsonProperty("switch_id") final SwitchId switchId,
                                    @JsonProperty("input_port") final Integer inputPort,
                                    @JsonProperty("output_port") final Integer outputPort,
                                    @JsonProperty("input_vlan_id") final Integer inputVlanId,
                                    @JsonProperty("input_inner_vlan_id") Integer inputInnerVlanId,
                                    @JsonProperty("transit_encapsulation_id") final Integer transitEncapsulationId,
                                    @JsonProperty("transit_encapsulation_type") final FlowEncapsulationType
                                          transitEncapsulationType,
                                    @JsonProperty("output_vlan_type") final OutputVlanType outputVlanType,
                                    @JsonProperty("bandwidth") final Long bandwidth,
                                    @JsonProperty("meter_id") final Long meterId,
                                    @JsonProperty("egress_switch_id") final SwitchId egressSwitchId,
                                    @JsonProperty("multi_table") final boolean multiTable,
                                    @JsonProperty("enable_lldp") final boolean enableLldp,
                                    @JsonProperty("enable_arp") final boolean enableArp,
                                    @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, inputVlanId, inputInnerVlanId,
                transitEncapsulationId, transitEncapsulationType, outputVlanType, bandwidth, meterId, egressSwitchId,
                multiTable, enableLldp, enableArp, mirrorConfig);
    }
}
