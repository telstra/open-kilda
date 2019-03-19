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

package org.openkilda.messaging.info.switches;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class MeterInfoEntry implements Serializable {

    @JsonProperty("meter_id")
    private Long meterId;

    @JsonProperty("cookie")
    private Long cookie;

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("rate")
    private Long rate;

    @JsonProperty("burst_size")
    private Long burstSize;

    @JsonProperty("flags")
    private String[] flags;

    @JsonProperty("actual")
    private MeterMisconfiguredInfoEntry actual;

    @JsonProperty("expected")
    private MeterMisconfiguredInfoEntry expected;

    @JsonCreator
    public MeterInfoEntry(@JsonProperty("meter_id") Long meterId,
                          @JsonProperty("cookie") Long cookie,
                          @JsonProperty("flow_id") String flowId,
                          @JsonProperty("rate") Long rate,
                          @JsonProperty("burst_size") Long burstSize,
                          @JsonProperty("flags") String[] flags,
                          @JsonProperty("actual") MeterMisconfiguredInfoEntry actual,
                          @JsonProperty("expected") MeterMisconfiguredInfoEntry expected) {
        this.meterId = meterId;
        this.cookie = cookie;
        this.flowId = flowId;
        this.actual = actual;
        this.expected = expected;
        this.rate = rate;
        this.burstSize = burstSize;
        this.flags = flags;
    }
}
