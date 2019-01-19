/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.history;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.Message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

@Getter
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryMessage extends Message {

    /**
     * Data of the information message.
     */
    @JsonProperty(PAYLOAD)
    private HistoryData data;

    @JsonCreator
    public HistoryMessage(
            @JsonProperty(TIMESTAMP) long timestamp,
            @JsonProperty(CORRELATION_ID) String correlationId,
            @JsonProperty(PAYLOAD) HistoryData data
    ) {
        super(timestamp, correlationId);
        this.data = data;
    }
}
