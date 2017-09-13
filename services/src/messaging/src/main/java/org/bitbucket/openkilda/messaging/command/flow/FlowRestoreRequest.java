package org.bitbucket.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.model.Flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Represents create flow northbound request.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        Utils.PAYLOAD})
public class FlowRestoreRequest extends CommandData {
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
     * Instance constructor.
     *
     * @param payload request payload
     * @throws IllegalArgumentException if payload is null
     */
    @JsonCreator
    public FlowRestoreRequest(@JsonProperty(Utils.PAYLOAD) Flow payload) {
        setPayload(payload);
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(Utils.PAYLOAD, payload)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(payload);
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

        FlowRestoreRequest that = (FlowRestoreRequest) object;
        return Objects.equals(getPayload(), that.getPayload());
    }
}
