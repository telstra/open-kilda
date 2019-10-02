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
 * Class represents egress flow installation info.
 * Transit vlan id is used in matching.
 * Output action depends on flow input and output vlan presence, but should at least contain transit vlan stripping.
 * Output vlan id is optional, because flow could be untagged on outgoing side.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "command",
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "input_port",
        "output_port",
        "transit_encapsulation_id",
        "transit_encapsulation_type",
        "output_vlan_id",
        "output_vlan_type"})
public class UpdateEgressFlow extends InstallEgressFlow {
    private static final long serialVersionUID = 4277194009876283547L;

    /**
     * Instance constructor.
     *
     * @param transactionId  transaction id
     * @param id             id of the flow
     * @param cookie         flow cookie
     * @param switchId       switch ID for flow installation
     * @param inputPort      input port of the flow
     * @param outputPort     output port of the flow
     * @param transitEncapsulationId  transit encapsulation id value
     * @param transitEncapsulationType  transit encapsulation type value
     * @param outputVlanId   output vlan id value
     * @param outputVlanType output vlan tag action
     * @param multiTable     multitable flag
     * @param applications   the applications on which the actions is performed.
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public UpdateEgressFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                             @JsonProperty(FLOW_ID) final String id,
                             @JsonProperty("cookie") final Long cookie,
                             @JsonProperty("switch_id") final SwitchId switchId,
                             @JsonProperty("input_port") final Integer inputPort,
                             @JsonProperty("output_port") final Integer outputPort,
                             @JsonProperty("transit_encapsulation_id") final Integer transitEncapsulationId,
                             @JsonProperty("transit_encapsulation_type") final FlowEncapsulationType
                                     transitEncapsulationType,
                             @JsonProperty("output_vlan_id") final Integer outputVlanId,
                             @JsonProperty("output_vlan_type") final OutputVlanType outputVlanType,
                             @JsonProperty("multi_table") final boolean multiTable,
                             @JsonProperty("applications") Set<FlowApplication> applications) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, transitEncapsulationId,
                transitEncapsulationType, outputVlanId, outputVlanType, multiTable, applications);

    }

    public UpdateEgressFlow(InstallEgressFlow command) {
        super(command.getTransactionId(), command.getId(), command.getCookie(), command.getSwitchId(),
                command.getInputPort(), command.getOutputPort(), command.getTransitEncapsulationId(),
                command.getTransitEncapsulationType(), command.getOutputVlanId(), command.getOutputVlanType(),
                command.isMultiTable(), command.getApplications());

    }
}
