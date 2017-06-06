package org.bitbucket.openkilda.messaging.payload.request;

import static com.google.common.base.Objects.toStringHelper;

import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents abstract northbound request by flow name.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "id"})
public class FlowIdRequestPayload implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The id of the flow.
     */
    @JsonProperty("id")
    protected String id;

    /**
     * Default constructor.
     */
    public FlowIdRequestPayload() {
    }

    /**
     * Instance constructor.
     *
     * @param   id  flow id
     *
     * @throws  IllegalArgumentException if flow id is null
     */
    @JsonCreator
    public FlowIdRequestPayload(@JsonProperty("id") final String id) {
        setId(id);
    }

    /**
     * Returns id of the flow.
     *
     * @return  id of the flow
     */
    public String getId() {
        return id;
    }

    /**
     * Sets id of the flow.
     *
     * @param   id  name of the flow
     */
    public void setId(final String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("need to set a id");
        }
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this).add("id", id).toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
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

        FlowIdRequestPayload that = (FlowIdRequestPayload) object;
        return Objects.equals(getId(), that.getId());
    }
}
