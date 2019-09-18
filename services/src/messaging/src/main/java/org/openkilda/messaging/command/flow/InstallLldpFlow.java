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
import static org.openkilda.messaging.Utils.OF_CONTROLLER_PORT;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonNaming(value = SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstallLldpFlow extends BaseInstallFlow {
    private static final long serialVersionUID = 2313171626703050068L;

    protected Long meterId;
    protected Integer encapsulationId;
    protected FlowEncapsulationType encapsulationType;

    /**
     * Instance constructor.
     *
     * @param transactionId  transaction id
     * @param id             id of the flow
     * @param cookie         flow cookie
     * @param switchId       switch ID for flow installation
     * @param inputPort      input port of the flow
     * @param encapsulationId  encapsulation id value
     * @param encapsulationType  encapsulation type value
     * @param meterId        flow meter id
     * @param multiTable     multitable flag
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public InstallLldpFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                           @JsonProperty(FLOW_ID) final String id,
                           @JsonProperty("cookie") final Long cookie,
                           @JsonProperty("switch_id") final SwitchId switchId,
                           @JsonProperty("input_port") final Integer inputPort,
                           @JsonProperty("encapsulation_id") final Integer encapsulationId,
                           @JsonProperty("encapsulation_type") final FlowEncapsulationType encapsulationType,
                           @JsonProperty("meter_id") final Long meterId,
                           @JsonProperty("multi_table") final boolean multiTable) {
        super(transactionId, id, cookie, switchId, inputPort, OF_CONTROLLER_PORT, multiTable);
        setMeterId(meterId);
        setEncapsulationId(encapsulationId);
        setEncapsulationType(encapsulationType);
    }

    /**
     * Sets meter id for the flow.
     *
     * @param meterId id for the flow
     */
    public void setMeterId(final Long meterId) {
        if (meterId != null && meterId <= 0L) {
            throw new IllegalArgumentException("Meter id value should be positive");
        }
        this.meterId = meterId;
    }
}
