package org.bitbucket.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.info.flow.FlowOperation;
import org.bitbucket.openkilda.messaging.model.Flow;

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
     * Default constructor.
     */
    public FlowRerouteRequest() {
    }

    /**
     * Instance constructor.
     *
     * @param payload flow operation payload
     * @param operation flow operation type
     * @throws IllegalArgumentException if payload is null
     */
    @JsonCreator
    public FlowRerouteRequest(@JsonProperty(Utils.PAYLOAD) Flow payload,
                              @JsonProperty("operation") FlowOperation operation) {
        setPayload(payload);
        setOperation(operation);
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
