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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * Represents flow operation.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        Utils.PAYLOAD,
        "operation",
        Utils.CORRELATION_ID})
public class FlowInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty(Utils.FLOW_ID)
    private String flowId;

    /**
     * The flow operation payload.
     */
    @JsonProperty(Utils.PAYLOAD)
    private ImmutablePair<Flow, Flow> payload;

    /**
     * Flow request correlation id.
     */
    @JsonProperty(Utils.CORRELATION_ID)
    protected String correlationId;

    /**
     * The flow operation type.
     */
    @JsonProperty("operation")
    private FlowOperation operation;

    /**
     * Last updated timestamp.
     */
    @JsonProperty("last_updated")
    private String lastUpdated;
    // TODO: How is lastUpdated used? Was added after reference in Flow.java / python / failed ATs

    /**
     * Default constructor.
     */
    public FlowInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId        flow Identifier
     * @param payload       flow operation payload
     * @param operation     flow operation type
     * @param lastUpdated   flow last updated time
     * @param correlationId flow request correlation id
     */
    @JsonCreator
    public FlowInfoData(@JsonProperty(Utils.FLOW_ID) final String flowId,
                        @JsonProperty(Utils.PAYLOAD) ImmutablePair<Flow, Flow> payload,
                        @JsonProperty("operation") FlowOperation operation,
                        @JsonProperty("last_updated") String lastUpdated,
                        @JsonProperty(Utils.CORRELATION_ID) String correlationId) {
        this.flowId = flowId;
        this.payload = payload;
        this.operation = operation;
        this.lastUpdated = lastUpdated;
        this.correlationId = correlationId;
    }

    /**
     * If no lastUpdate specified, then use the current time on the current machine.
     *
     * @param flowId        flow Identifier
     * @param payload       flow operation payload
     * @param operation     flow operation type
     * @param correlationId flow request correlation id
     */
    public FlowInfoData(final String flowId,
                        ImmutablePair<Flow, Flow> payload,
                        FlowOperation operation,
                        String correlationId) {
        this(flowId, payload, operation,
                new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()),
                correlationId);
    }

    /**
     * Gets flow id.
     *
     * @return flow id
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Sets flow id.
     *
     * @param flowId flow id
     */
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    /**
     * Gets flow operation payload.
     *
     * @return flow operation payload
     */
    public ImmutablePair<Flow, Flow> getPayload() {
        return payload;
    }

    /**
     * Sets flow operation payload.
     *
     * @param payload flow operation payload
     */
    public void setPayload(ImmutablePair<Flow, Flow> payload) {
        this.payload = payload;
    }

    /**
     * Gets flow operation type.
     *
     * @return flow operation type
     */
    public FlowOperation getOperation() {
        return operation;
    }

    /**
     * Sets flow operation type.
     *
     * @param operation flow operation type
     */
    public void setOperation(FlowOperation operation) {
        this.operation = operation;
    }

    /**
     * @return The last time this was updated.
     */
    public String getLastUpdated() { return lastUpdated; }

    /**
     * @param lastUpdated Update the last updated field
     */
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    /**
     * Gets flow request correlation id.
     *
     * @return flow request correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets flow request correlation id.
     *
     * @param correlationId flow request correlation id
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add(Utils.FLOW_ID, flowId)
                .add(Utils.PAYLOAD, payload)
                .add("operation", operation)
                .add(Utils.CORRELATION_ID, correlationId)
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

        FlowInfoData that = (FlowInfoData) object;
        return Objects.equals(getFlowId(), that.getFlowId())
                && Objects.equals(getOperation(), that.getOperation())
                && Objects.equals(getPayload(), that.getPayload())
                && Objects.equals(getCorrelationId(), that.getCorrelationId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getPayload(), getOperation(), getCorrelationId());
    }
}
