package org.bitbucket.openkilda.messaging.info.flow;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * Represents flow data.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        Utils.PAYLOAD})
public class FlowInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The flow operation payload.
     */
    @JsonProperty(Utils.PAYLOAD)
    private ImmutablePair<Flow, Flow> payload;

    /**
     *  The flow operation type.
     */
    @JsonProperty("operation")
    private FlowOperation operation;

    @JsonCreator
    public FlowInfoData(@JsonProperty(Utils.PAYLOAD) ImmutablePair<Flow, Flow> payload,
                        @JsonProperty("operation") FlowOperation operation) {
        this.payload = payload;
        this.operation = operation;
    }

    public ImmutablePair<Flow, Flow> getPayload() {
        return payload;
    }

    public void setPayload(ImmutablePair<Flow, Flow> payload) {
        this.payload = payload;
    }

    public FlowOperation getOperation() {
        return operation;
    }

    public void setOperation(FlowOperation operation) {
        this.operation = operation;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add(Utils.PAYLOAD, payload)
                .add("operation", operation)
                .toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        FlowInfoData that = (FlowInfoData) object;
        return Objects.equals(getPayload(), that.getPayload())
                && Objects.equals(getOperation(), that.getOperation());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPayload(), getOperation());
    }
}
