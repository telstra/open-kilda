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

package org.openkilda.messaging.command.discovery;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an command for network dump.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "requester"})
public class HealthCheckCommandData extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Requester.
     */
    @JsonProperty("requester")
    private String requester;

    /**
     * Default constructor.
     */
    public HealthCheckCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param requester requester
     */
    @JsonCreator
    public HealthCheckCommandData(@JsonProperty("requester") String requester) {
        this.requester = requester;
    }

    /**
     * Returns requester.
     *
     * @return requester
     */
    public String getRequester() {
        return requester;
    }

    /**
     * Sets requester.
     *
     * @param requester requester
     */
    public void setRequester(String requester) {
        this.requester = requester;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("requester", requester)
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

        HealthCheckCommandData that = (HealthCheckCommandData) object;
        return Objects.equals(getRequester(), that.getRequester());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(requester);
    }
}
