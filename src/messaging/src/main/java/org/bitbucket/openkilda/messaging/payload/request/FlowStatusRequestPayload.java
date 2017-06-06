package org.bitbucket.openkilda.messaging.payload.request;

import static com.google.common.base.Objects.toStringHelper;

import org.bitbucket.openkilda.messaging.payload.response.FlowStatusType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents abstract northbound request by flow status.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "status"})
public class FlowStatusRequestPayload implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The status of the flows.
     */
    @JsonProperty("status")
    protected FlowStatusType status;

    /**
     * Instance constructor.
     *
     * @param   status  flows status
     *
     * @throws  IllegalArgumentException if flows status is null
     */
    @JsonCreator
    public FlowStatusRequestPayload(@JsonProperty("status") final FlowStatusType status) {
        setStatus(status);
    }

    /**
     * Returns status of the flows.
     *
     * @return  status of the flows
     */
    public FlowStatusType getStatus() {
        return status;
    }

    /**
     * Sets status of the flows.
     *
     * @param   status  status of the flows
     */
    public void setStatus(final FlowStatusType status) {
        if (status == null) {
            throw new IllegalArgumentException("need to set a status");
        }
        this.status = status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this).add("status", status).toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(status);
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

        FlowStatusRequestPayload that = (FlowStatusRequestPayload) object;
        return Objects.equals(getStatus(), that.getStatus());
    }
}
