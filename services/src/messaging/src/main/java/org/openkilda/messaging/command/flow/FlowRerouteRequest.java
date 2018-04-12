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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an command for flow path recomputation.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        Utils.PAYLOAD,
        "operation"})
public class FlowRerouteRequest extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The request payload.
     */
    @JsonProperty(Utils.PAYLOAD)
    protected Flow payload;

    /**
     * The flow operation type.
     */
    @JsonProperty("operation")
    private FlowOperation operation;

    /**
     * Update flow even if path will not be changed.
     */
    @JsonProperty("force")
    private boolean force;

    /**
     * Default constructor.
     */
    public FlowRerouteRequest() {
    }

    public FlowRerouteRequest(Flow payload, FlowOperation operation) {
        setPayload(payload);
        setOperation(operation);
    }
    /**
     * Instance constructor.
     *
     * @param payload flow operation payload
     * @param operation flow operation type
     * @param force try to update flow even if path won't be changed.
     * @throws IllegalArgumentException if payload is null
     */
    @JsonCreator
    public FlowRerouteRequest(@JsonProperty(Utils.PAYLOAD) Flow payload,
                              @JsonProperty("operation") FlowOperation operation,
                              @JsonProperty("force") boolean force) {
        setPayload(payload);
        setOperation(operation);
        setForce(force);
    }

    /**
     * Returns request payload.
     *
     * @return request payload
     */
    public Flow getPayload() {
        return payload;
    }

    /**
     * Sets request payload.
     *
     * @param payload request payload
     */
    public void setPayload(Flow payload) {
        if (payload == null) {
            throw new IllegalArgumentException("need to set payload");
        }
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

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(Utils.PAYLOAD, payload)
                .add("operation", operation)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getPayload(), getOperation());
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

        FlowRerouteRequest that = (FlowRerouteRequest) object;
        return Objects.equals(getPayload(), that.getPayload())
                && Objects.equals(getOperation(), that.getOperation());
    }
}
