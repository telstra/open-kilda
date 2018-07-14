/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.info.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationMeasures {
    @JsonProperty("network_latency")
    private long networkLatency;

    @JsonProperty("sender_latency")
    private long senderLatency;

    @JsonProperty("recipient_latency")
    private long recipientLatency;

    @JsonCreator
    public VerificationMeasures(
            @JsonProperty("network_latency") long networkLatency,
            @JsonProperty("sender_latency") long senderLatency,
            @JsonProperty("recipient_latency") long recipientLatency) {
        this.networkLatency = networkLatency;
        this.senderLatency = senderLatency;
        this.recipientLatency = recipientLatency;
    }
}
