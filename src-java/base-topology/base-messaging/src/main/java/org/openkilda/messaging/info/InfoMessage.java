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

package org.openkilda.messaging.info;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DESTINATION;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.REGION;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.bluegreen.kafka.TransportAdapter;
import org.openkilda.bluegreen.kafka.TransportErrorReport;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Optional;

/**
 * Class represents information message.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InfoMessage extends Message implements TransportAdapter {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the information message.
     */
    @JsonProperty(PAYLOAD)
    private InfoData data;

    @JsonProperty(REGION)
    private String region;

    /**
     * Instance constructor.
     *
     * @param data          info message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    @Builder(toBuilder = true)
    public InfoMessage(@JsonProperty(PAYLOAD) final InfoData data,
                       @JsonProperty(TIMESTAMP) final long timestamp,
                       @JsonProperty(CORRELATION_ID) final String correlationId,
                       @JsonProperty(DESTINATION) final Destination destination,
                       @JsonProperty(REGION) final String region,
                       @JsonProperty("cookie") MessageCookie cookie) {
        super(timestamp, correlationId, destination, cookie);
        this.region = region;
        this.data = data;
    }

    public InfoMessage(InfoData data, long timestamp, String correlationId, Destination destination, String region) {
        this(data, timestamp, correlationId, destination, region, null);
    }

    /**
     * Instance constructor.
     *
     * @param data          info message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     */
    public InfoMessage(final InfoData data, final long timestamp, final String correlationId) {
        this(data, timestamp, correlationId, null, null, null);
    }

    /**
     * Instance constructor.
     *
     * @param data          info message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param region        floodlight region identifier
     */
    public InfoMessage(final InfoData data, final long timestamp, final String correlationId, final String region) {
        this(data, timestamp, correlationId, null, region, null);
    }

    public InfoMessage(InfoData data, String correlationId, MessageCookie cookie) {
        this(data, System.currentTimeMillis(), correlationId, null, null, cookie);
    }

    @JsonIgnore
    @Override
    public Optional<TransportErrorReport> getErrorReport() {
        if (data instanceof TransportAdapter) {
            return ((TransportAdapter) data).getErrorReport();
        }
        return Optional.empty();
    }
}
