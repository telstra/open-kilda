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

import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.util.UUID;

@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UniFlowVerificationResponse extends InfoData {
    @JsonProperty("ping_success")
    private boolean pingSuccess;

    @JsonProperty("error")
    private FlowVerificationErrorCode error;

    @JsonProperty("measures")
    private VerificationMeasures measures;

    @JsonProperty("request")
    private UniFlowVerificationRequest request;

    @JsonCreator
    public UniFlowVerificationResponse(
            @JsonProperty("ping_success") boolean pingSuccess,
            @JsonProperty("error") FlowVerificationErrorCode error,
            @JsonProperty("network_latency") VerificationMeasures measures,
            @JsonProperty("request") UniFlowVerificationRequest request) {
        this.pingSuccess = pingSuccess;
        this.error = error;
        this.measures = measures;
        this.request = request;
    }

    public UniFlowVerificationResponse(UniFlowVerificationRequest request, VerificationMeasures measures) {
        this(true, null, measures, request);
    }

    public UniFlowVerificationResponse(
            UniFlowVerificationRequest request, FlowVerificationErrorCode error) {
        this(false, error, null, request);
    }

    @JsonIgnore
    public String getFlowId() {
        return getRequest().getFlowId();
    }

    @JsonIgnore
    public UUID getPacketId() {
        return getRequest().getPacketId();
    }
}
