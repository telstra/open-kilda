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

package org.openkilda.messaging.nbtopology.response;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;

@Getter
public class ChunkedInfoMessage extends InfoMessage {

    @JsonProperty("total_items")
    private int totalItems;

    @JsonProperty("next_request_id")
    private String nextRequestId;

    public ChunkedInfoMessage(@JsonProperty(PAYLOAD) final InfoData data,
                              @JsonProperty(TIMESTAMP) final long timestamp,
                              @JsonProperty(CORRELATION_ID) final String correlationId,
                              @JsonProperty("total_items") int totalItems,
                              @JsonProperty("next_request_id") String nextRequestId) {
        super(data, timestamp, correlationId);
        this.totalItems = totalItems;
        this.nextRequestId = nextRequestId;
    }
}
