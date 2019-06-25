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

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.messaging.Utils;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;
import java.util.UUID;

/**
 * Class represents transit flow installation info.
 * There is no output action for this type of flow.
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
        "transit_encapsulation_id",
        "transit_encapsulation_type"
        })
public class InstallTransitFlow extends BaseInstallFlow {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The transit encapsulation id value.
     */
    @JsonProperty("transit_encapsulation_id")
    protected Integer transitEncapsulationId;

    /**
     * The transit encapsulation type.
     */
    @JsonProperty("transit_encapsulation_type")
    protected FlowEncapsulationType transitEncapsulationType;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id            id of the flow
     * @param cookie        flow cookie
     * @param switchId      switch ID for flow installation
     * @param inputPort     input port of the flow
     * @param outputPort    output port of the flow
     * @param transitEncapsulationId transit encapsulation id value
     * @param transitEncapsulationType transit encapsulation type
     * @throws IllegalArgumentException if any of parameters parameters is null
     */
    @JsonCreator
    public InstallTransitFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                              @JsonProperty(FLOW_ID) final String id,
                              @JsonProperty("cookie") final Long cookie,
                              @JsonProperty("switch_id") final SwitchId switchId,
                              @JsonProperty("input_port") final Integer inputPort,
                              @JsonProperty("output_port") final Integer outputPort,
                              @JsonProperty("transit_encapsulation_id") final Integer transitEncapsulationId,
                              @JsonProperty("transit_encapsulation_type") final FlowEncapsulationType
                                          transitEncapsulationType) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort);
        setTransitEncapsulationType(transitEncapsulationType);
        setTransitEncapsulationId(transitEncapsulationId);
    }

    /**
     * Returns transit encapsulation id of the flow.
     *
     * @return transit encapsulation id of the flow
     */
    public Integer getTransitEncapsulationId() {
        return transitEncapsulationId;
    }

    /**
     * Sets transit encapsulation id of the flow.
     *
     * @param transitEncapsulationId encapsulation id of the flow
     */
    public void setTransitEncapsulationId(final Integer transitEncapsulationId) {
        if (transitEncapsulationId == null) {
            throw new IllegalArgumentException("need to set transit_encapsulation_id");
        }
        if (FlowEncapsulationType.TRANSIT_VLAN.equals(transitEncapsulationType)) {
            if (!Utils.validateVlanRange(transitEncapsulationId) || transitEncapsulationId == 0L) {
                throw new IllegalArgumentException("need to set valid value for transit_encapsulation_id");
            }
        } else if (FlowEncapsulationType.VXLAN.equals(transitEncapsulationType)) {
            if (!Utils.validateVxlanRange(transitEncapsulationId)) {
                throw new IllegalArgumentException("need to set valid value for transit_encapsulation_id");
            }
        }
        this.transitEncapsulationId = transitEncapsulationId;
    }

    /**
     * Returns transit encapsulation type.
     *
     * @return transit flow encapsulation type
     */
    public FlowEncapsulationType getTransitEncapsulationType() {
        return transitEncapsulationType;
    }

    /**
     * Set transit encapsulation type of the flow.
     *
     * @param transitEncapsulationType flow encapsulation type
     */
    public void setTransitEncapsulationType(FlowEncapsulationType transitEncapsulationType) {
        if (transitEncapsulationType == null) {
            throw new IllegalArgumentException("need to set transit_encapsulation_type");
        }
        this.transitEncapsulationType = transitEncapsulationType;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TRANSACTION_ID, transactionId)
                .add(FLOW_ID, id)
                .add("cookie", cookie)
                .add("switch_id", switchId)
                .add("input_port", inputPort)
                .add("output_port", outputPort)
                .add("transit_encapsulation_id", transitEncapsulationId)
                .add("transit_encapsulation_type", transitEncapsulationType)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        InstallTransitFlow that = (InstallTransitFlow) object;
        return Objects.equals(getTransactionId(), that.getTransactionId())
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getCookie(), that.getCookie())
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getInputPort(), that.getInputPort())
                && Objects.equals(getOutputPort(), that.getOutputPort())
                && Objects.equals(getTransitEncapsulationId(), that.getTransitEncapsulationId())
                && Objects.equals(getTransitEncapsulationType(), that.getTransitEncapsulationType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId, id, cookie, switchId, inputPort, outputPort, transitEncapsulationId,
                transitEncapsulationType);
    }
}
