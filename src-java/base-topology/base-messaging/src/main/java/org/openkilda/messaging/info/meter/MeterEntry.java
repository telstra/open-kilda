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

package org.openkilda.messaging.info.meter;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class MeterEntry extends InfoData implements Serializable {

    @JsonProperty("meter_id")
    private long meterId;
    @JsonProperty("rate")
    private long rate;
    @JsonProperty("burst_size")
    private long burstSize;
    @JsonProperty("version")
    private String version;
    @JsonProperty("flags")
    private String[] flags;

    @JsonCreator
    public MeterEntry(
            @JsonProperty("meter_id") long meterId,
            @JsonProperty("rate") long rate,
            @JsonProperty("burst_size") long burstSize,
            @JsonProperty("version") String version,
            @JsonProperty("flags") String[] flags) {
        this.meterId = meterId;
        this.rate = rate;
        this.burstSize = burstSize;
        this.version = version;
        this.flags = flags;
    }
}
