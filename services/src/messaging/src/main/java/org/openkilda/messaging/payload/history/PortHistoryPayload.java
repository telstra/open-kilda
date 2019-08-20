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

package org.openkilda.messaging.payload.history;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PortHistoryPayload extends InfoData {
    @JsonProperty("switch_id")
    private SwitchId switchId;
    @JsonProperty("port_number")
    private int portNumber;
    @JsonProperty("status")
    private PortStatus status;
    @JsonProperty("bounced_times")
    private int bouncedTimes;
    @JsonProperty("bouncing_started_time")
    private long bouncingStartedTime;
    @JsonProperty("bouncing_ends_time")
    private long bouncingEndedTime;

    @JsonCreator
    public PortHistoryPayload(@JsonProperty("switch_id") SwitchId switchId,
                              @JsonProperty("port_number") int portNumber,
                              @JsonProperty("status") PortStatus status,
                              @JsonProperty("bounced_times") int bouncedTimes,
                              @JsonProperty("bouncing_started_time") long bouncingStartedTime,
                              @JsonProperty("bouncing_ends_time") long bouncingEndedTime) {
        this.switchId = switchId;
        this.portNumber = portNumber;
        this.status = status;
        this.bouncedTimes = bouncedTimes;
        this.bouncingStartedTime = bouncingStartedTime;
        this.bouncingEndedTime = bouncingEndedTime;
    }
}
