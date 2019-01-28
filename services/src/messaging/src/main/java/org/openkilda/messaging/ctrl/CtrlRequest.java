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

package org.openkilda.messaging.ctrl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DESTINATION;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.ROUTE;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents CTRL request message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        DESTINATION,
        PAYLOAD,
        TIMESTAMP,
        CORRELATION_ID})
public class CtrlRequest extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty(ROUTE)
    private String route = "*";

    /**
     * Data of the ctrl message.
     */
    @JsonProperty(PAYLOAD)
    private RequestData data;

    /**
     * Instance constructor.
     *
     * @param data          ctrl message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    public CtrlRequest(@JsonProperty(ROUTE) String route,
                       @JsonProperty(PAYLOAD) final RequestData data,
                       @JsonProperty(TIMESTAMP) final long timestamp,
                       @JsonProperty(CORRELATION_ID) final String correlationId,
                       @JsonProperty(DESTINATION) final Destination destination) {
        super(timestamp, correlationId, destination);
        this.route = route;
        this.data = data;
    }

    /**
     * Returns payload of the ctrl message.
     *
     * @return ctrl message payload
     */
    public RequestData getData() {
        return data;
    }

    public String getRoute() {
        return route;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add(CORRELATION_ID, correlationId)
                .add(DESTINATION, destination)
                .add(PAYLOAD, data)
                .toString();
    }
}
