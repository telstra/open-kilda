/* Copyright 2020 Telstra Open Source
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

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.messaging.Utils;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
public class InstallServer42IngressFlow extends InstallTransitFlow {
    private static final long serialVersionUID = 2944794560559058839L;

    @JsonProperty("customer_port")
    protected int customerPort;

    @JsonProperty("input_vlan_id")
    protected Integer inputVlanId;

    @JsonProperty("input_inner_vlan_id")
    protected int inputInnerVlanId;

    @JsonProperty("egress_switch_id")
    protected SwitchId egressSwitchId;

    @JsonProperty("server42_mac_address")
    protected MacAddress server42MacAddress;

    /**
     * Instance constructor.
     *
     * @param transactionId  transaction id
     * @param id             id of the flow
     * @param cookie         flow cookie
     * @param switchId       switch ID for flow installation
     * @param inputPort      input port of the flow (server42 packet allways comes from server42 port)
     * @param outputPort     output port of the flow
     * @param customerPort   port from which comes customer's packets. need for matching by metadata
     * @param inputVlanId    input vlan id value
     * @param inputInnerVlanId    input inner vlan id value
     * @param transitEncapsulationId  transit encapsulation id value
     * @param transitEncapsulationType  transit encapsulation type value
     * @param egressSwitchId id of the ingress switch
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @Builder
    @JsonCreator
    public InstallServer42IngressFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                                      @JsonProperty(FLOW_ID) final String id,
                                      @JsonProperty("cookie") final Long cookie,
                                      @JsonProperty("switch_id") final SwitchId switchId,
                                      @JsonProperty("input_port") final Integer inputPort,
                                      @JsonProperty("output_port") final Integer outputPort,
                                      @JsonProperty("customer_port") final Integer customerPort,
                                      @JsonProperty("input_vlan_id") final Integer inputVlanId,
                                      @JsonProperty("input_inner_vlan_id") Integer inputInnerVlanId,
                                      @JsonProperty("transit_encapsulation_id") final Integer transitEncapsulationId,
                                      @JsonProperty("transit_encapsulation_type") final FlowEncapsulationType
                                          transitEncapsulationType,
                                      @JsonProperty("egress_switch_id") final SwitchId egressSwitchId,
                                      @JsonProperty("server42_mac_address") MacAddress server42MacAddress,
                                      @JsonProperty("multi_table") final boolean multiTable) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, transitEncapsulationId,
                transitEncapsulationType, multiTable);
        setCustomerPort(customerPort);
        setInputVlanId(inputVlanId);
        setInputInnerVlanId(inputInnerVlanId);
        setEgressSwitchId(egressSwitchId);
        setServer42MacAddress(server42MacAddress);
    }

    /**
     * Sets input vlan id value.
     *
     * @param inputVlanId input vlan id value
     */
    public void setInputVlanId(final Integer inputVlanId) {
        if (inputVlanId == null) {
            this.inputVlanId = 0;
        } else if (Utils.validateVlanRange(inputVlanId)) {
            this.inputVlanId = inputVlanId;
        } else {
            throw new IllegalArgumentException(format("Invalid input_vlan_id = %s", inputVlanId));
        }
    }
}
