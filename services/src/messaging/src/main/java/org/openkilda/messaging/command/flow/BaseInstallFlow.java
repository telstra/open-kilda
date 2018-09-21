/* Copyright 2017 Telstra Open Source
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

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents abstract flow installation info.
 * Every flow installation command should contain these class properties.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "input_port",
        "output_port"})
public class BaseInstallFlow extends BaseFlow {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Input port flow matching.
     */
    @JsonProperty("input_port")
    protected Integer inputPort;

    /**
     * Output port for flow action.
     */
    @JsonProperty("output_port")
    protected Integer outputPort;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id            id of the flow
     * @param cookie        cookie of the flow
     * @param switchId      switch id for flow installation
     * @param inPort        input port of the flow
     * @param outPort       output port of the flow
     * @throws IllegalArgumentException if mandatory parameter is null
     */
    @JsonCreator
    public BaseInstallFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                           @JsonProperty(FLOW_ID) final String id,
                           @JsonProperty("cookie") final Long cookie,
                           @JsonProperty("switch_id") final SwitchId switchId,
                           @JsonProperty("input_port") final Integer inPort,
                           @JsonProperty("output_port") final Integer outPort) {
        super(transactionId, id, cookie, switchId);
        setInputPort(inPort);
        setOutputPort(outPort);
    }

    /**
     * Returns input port of the flow.
     *
     * @return inout port of the flow
     */
    public Integer getInputPort() {
        return inputPort;
    }

    /**
     * Sets input port of the flow.
     *
     * @param inputPort input port of the flow
     */
    public void setInputPort(final Integer inputPort) {
        if (inputPort == null) {
            throw new IllegalArgumentException("need to set input_port");
        } else if (inputPort < 0L) {
            throw new IllegalArgumentException("need to set positive value for input_port");
        }
        this.inputPort = inputPort;
    }

    /**
     * Returns output port of the flow.
     *
     * @return output port of the flow
     */
    public Integer getOutputPort() {
        return outputPort;
    }

    /**
     * Sets output port of the flow.
     *
     * @param outputPort output port of the flow
     */
    public void setOutputPort(final Integer outputPort) {
        if (outputPort == null) {
            throw new IllegalArgumentException("need to set output_port");
        } else if (outputPort < 0L) {
            throw new IllegalArgumentException("need to set positive value for output_port");
        }
        this.outputPort = outputPort;
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

        BaseInstallFlow that = (BaseInstallFlow) object;
        return Objects.equals(getTransactionId(), that.getTransactionId())
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getCookie(), that.getCookie())
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getInputPort(), that.getInputPort())
                && Objects.equals(getOutputPort(), that.getOutputPort());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId, id, cookie, switchId, inputPort, outputPort);
    }
}
