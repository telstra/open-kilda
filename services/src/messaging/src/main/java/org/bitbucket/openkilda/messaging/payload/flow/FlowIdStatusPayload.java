package org.bitbucket.openkilda.messaging.payload.flow;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.bitbucket.openkilda.messaging.Utils.FLOW_ID;

import com.fasterxml.jackson.annotation.JsonCreator;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        FLOW_ID,
        "status"})
public class FlowIdStatusPayload implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty(FLOW_ID)
    private String id;

    /**
     * Flow status.
     */
    @JsonProperty("status")
    private FlowStatusType status;

    /**
     * Instance constructor.
     */
    public FlowIdStatusPayload() {
    }

    /**
     * Instance constructor.
     *
     * @param id flow id
     */
    public FlowIdStatusPayload(final String id) {
        setId(id);
    }

    /**
     * Instance constructor.
     *
     * @param id     flow id
     * @param status flow status
     */
    @JsonCreator
    public FlowIdStatusPayload(@JsonProperty(FLOW_ID) final String id,
                               @JsonProperty("status") final FlowStatusType status) {
        setId(id);
        setStatus(status);
    }

    /**
     * Gets flow id.
     *
     * @return flow id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets flow id.
     *
     * @param id flow id
     */
    public void setId(final String id) {
        this.id = id;
    }

    /**
     * Gets flow status.
     *
     * @return flow status
     */
    public FlowStatusType getStatus() {
        return status;
    }

    /**
     * Sets flow status.
     *
     * @param status flow status
     */
    public void setStatus(final FlowStatusType status) {
        this.status = status;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(FLOW_ID, id)
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
        if (obj == null || !(obj instanceof FlowIdStatusPayload)) {
            return false;
        }

        FlowIdStatusPayload that = (FlowIdStatusPayload) obj;
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
