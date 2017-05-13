package org.bitbucket.openkilda.messaging.payload.response;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow status representation class.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "status"})
public class FlowStatusResponsePayload implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty("id")
    private String id;

    /**
     * Flow status.
     */
    @JsonProperty("status")
    private FlowStatusType status;

    /**
     * Constructs the flow model.
     */
    public FlowStatusResponsePayload() {
    }

    /**
     * Instance constructor.
     *
     * @param   id      flow id
     * @param   status  flow status
     */
    @JsonCreator
    public FlowStatusResponsePayload(@JsonProperty("id") final String id,
                                     @JsonProperty("status") final FlowStatusType status) {
        setId(id);
        setStatus(status);
    }

    /**
     * Gets flow id.
     *
     * @return  flow id
     */
    public String getId() {
        return id;
    }

    /**
     * Gets flow status.
     *
     * @return  flow status
     */
    public FlowStatusType getStatus() {
        return status;
    }

    /**
     * Sets flow id.
     *
     * @param   id  flow id
     */
    public void setId(final String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("need to set id");
        }
        this.id = id;
    }


    /**
     * Sets flow status.
     *
     * @param   status  flow status
     */
    public void setStatus(final FlowStatusType status) {
        if (status == null) {
            throw new IllegalArgumentException("need to set status");
        }
        this.status = status;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", id)
                .add("status", status)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof FlowStatusResponsePayload)) {
            return false;
        }

        FlowStatusResponsePayload that = (FlowStatusResponsePayload) obj;
        return Objects.equals(getId(), that.getId())
                && Objects.equals(getStatus(), that.getStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, status);
    }
}
