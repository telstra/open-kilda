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

package org.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents get flow northbound request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowGetRequest extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The request payload.
     */
    @JsonProperty(PAYLOAD)
    protected FlowIdStatusPayload payload;

    public FlowGetRequest() {
    }

    /**
     * Instance constructor.
     *
     * @param payload request payload
     * @throws IllegalArgumentException if payload is null
     */
    @JsonCreator
    public FlowGetRequest(@JsonProperty(PAYLOAD) final FlowIdStatusPayload payload) {
        setPayload(payload);
    }

    /**
     * Returns request payload.
     *
     * @return request payload
     */
    public FlowIdStatusPayload getPayload() {
        return payload;
    }

    /**
     * Sets request payload.
     *
     * @param payload request payload
     */
    public void setPayload(final FlowIdStatusPayload payload) {
        this.payload = payload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(PAYLOAD, payload)
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

        FlowGetRequest that = (FlowGetRequest) object;
        return Objects.equals(getPayload(), that.getPayload());
    }
}
