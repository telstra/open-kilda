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

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.messaging.model.PingMeters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.UUID;

@Value
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UniFlowPingResponse extends InfoData {
    @JsonProperty("ping_success")
    private boolean pingSuccess;

    @JsonProperty("error")
    private Errors error;

    @JsonProperty("meters")
    private PingMeters meters;

    @JsonProperty("ping")
    private Ping ping;

    @JsonCreator
    public UniFlowPingResponse(
            @JsonProperty("ping_success") boolean pingSuccess,
            @JsonProperty("error") Errors error,
            @JsonProperty("meters") PingMeters meters,
            @JsonProperty("ping") Ping ping) {
        this.pingSuccess = pingSuccess;
        this.error = error;
        this.meters = meters;
        this.ping = ping;
    }

    public UniFlowPingResponse(Ping ping, PingMeters meters, Ping.Errors error) {
        this(error == null, error, meters, ping);
    }

    @JsonIgnore
    public UUID getPacketId() {
        return getPing().getPingId();
    }
}
