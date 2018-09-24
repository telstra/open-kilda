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
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Class represents information message.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InfoMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the information message.
     */
    @JsonProperty(PAYLOAD)
    private InfoData data;

    /**
     * Instance constructor.
     *
     * @param data          info message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    public InfoMessage(@JsonProperty(PAYLOAD) final InfoData data,
                       @JsonProperty(TIMESTAMP) final long timestamp,
                       @JsonProperty(CORRELATION_ID) final String correlationId,
                       @JsonProperty(DESTINATION) final Destination destination) {
        super(timestamp, correlationId, destination);
        this.data = data;
    }

    /**
     * Instance constructor.
     *
     * @param data          info message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     */
    public InfoMessage(final InfoData data,
                       final long timestamp,
                       final String correlationId) {
        super(timestamp, correlationId);
        this.data = data;
    }

}
