package org.bitbucket.openkilda.messaging.info.discovery;

import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an isl info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "id",
        "state"})
public class HealthCheckInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Topology id.
     */
    @JsonProperty("id")
    protected String id;

    /**
     * Topology state.
     */
    @JsonProperty("state")
    protected String state;

    public HealthCheckInfoData() {
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getState());
    }

    @JsonCreator
    public HealthCheckInfoData(@JsonProperty("id") String id,
                               @JsonProperty("state") String state) {
        this.id = id;
        this.state = state;

    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("state", state)
                .toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
