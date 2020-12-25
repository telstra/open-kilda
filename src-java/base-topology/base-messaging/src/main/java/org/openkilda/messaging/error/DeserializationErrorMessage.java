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

package org.openkilda.messaging.error;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.bluegreen.kafka.DeserializationError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeserializationErrorMessage extends ErrorMessage implements DeserializationError {

    @JsonCreator
    public DeserializationErrorMessage(@JsonProperty(PAYLOAD) final ErrorData data,
                                       @JsonProperty(TIMESTAMP) final long timestamp,
                                       @JsonProperty(CORRELATION_ID) final String correlationId) {
        super(data, timestamp, correlationId);
    }

    @Override
    public String getDeserializationErrorMessage() {
        return String.format("Couldn't deserialize kafka message. ErrorData: %s, timestamp: %d, correlationId %s",
                getData(), getTimestamp(), getCorrelationId());
    }
}
