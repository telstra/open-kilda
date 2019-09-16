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

package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Set;
import java.util.UUID;

/**
 * Class represents ingress flow installation info.
 * Transit vlan id is used in output action.
 * Output action is always push transit vlan tag.
 * Input vlan id is optional, because flow could be untagged on ingoing side.
 * Bandwidth and meter id are used for flow throughput limitation.
 */
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
public class UpdateIngressFlow extends InstallIngressFlow {
    private static final long serialVersionUID = 7144549070441946589L;


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
     * @param transitEncapsulationId transit encapsulation id value
     * @param transitEncapsulationType transit encapsulation type value
     * @param outputVlanType output vlan type action
     * @param bandwidth flow bandwidth
     * @param meterId flow meter id
     * @param ingressSwitchId id of the ingress switch
     * @param multiTable multitable flag
     * @param enableLldp lldp flag. Packets will be send to LLDP rule if True.
     * @param applications the applications on which the actions is performed.
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public UpdateIngressFlow(@JsonProperty(TRANSACTION_ID) UUID transactionId,
                             @JsonProperty(FLOW_ID) String id,
                             @JsonProperty("cookie") Long cookie,
                             @JsonProperty("switch_id") SwitchId switchId,
                             @JsonProperty("input_port") Integer inputPort,
                             @JsonProperty("output_port") Integer outputPort,
                             @JsonProperty("input_vlan_id") Integer inputVlanId,
                             @JsonProperty("transit_encapsulation_id")
                                     Integer transitEncapsulationId,
                             @JsonProperty("transit_encapsulation_type")
                                     FlowEncapsulationType transitEncapsulationType,
                             @JsonProperty("output_vlan_type") OutputVlanType outputVlanType,
                             @JsonProperty("bandwidth") Long bandwidth,
                             @JsonProperty("meter_id") Long meterId,
                             @JsonProperty("ingress_switch_id") SwitchId ingressSwitchId,
                             @JsonProperty("multi_table") boolean multiTable,
                             @JsonProperty("enable_lldp") boolean enableLldp,
                             @JsonProperty("applications") Set<FlowApplication> applications) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, inputVlanId, transitEncapsulationId,
                transitEncapsulationType, outputVlanType, bandwidth, meterId, ingressSwitchId, multiTable,
                enableLldp, applications);

    }

    public UpdateIngressFlow(InstallIngressFlow command) {
        super(command.getTransactionId(), command.getId(), command.getCookie(), command.getSwitchId(),
                command.getInputPort(), command.getOutputPort(), command.getInputVlanId(),
                command.getTransitEncapsulationId(), command.getTransitEncapsulationType(), command.getOutputVlanType(),
                command.getBandwidth(), command.getMeterId(), command.getIngressSwitchId(), command.isMultiTable(),
                command.isEnableLldp(), command.getApplications());

    }
}
