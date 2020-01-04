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

package org.openkilda.messaging.info.discovery;

import org.openkilda.messaging.info.InfoData;

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

    /**
     * Default constructor.
     */
    public HealthCheckInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param id    component id
     * @param state component state
     */
    @JsonCreator
    public HealthCheckInfoData(@JsonProperty("id") String id,
                               @JsonProperty("state") String state) {
        this.id = id;
        this.state = state;

    }

    /**
     * Gets component id.
     *
     * @return component id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets component id.
     *
     * @param id component id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets component state.
     *
     * @return component state
     */
    public String getState() {
        return state;
    }

    /**
     * Sets component state.
     *
     * @param state component state
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getId(), getState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("state", state)
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

        HealthCheckInfoData that = (HealthCheckInfoData) object;
        return Objects.equals(getId(), that.getId())
                && Objects.equals(getState(), that.getState());
    }
}
