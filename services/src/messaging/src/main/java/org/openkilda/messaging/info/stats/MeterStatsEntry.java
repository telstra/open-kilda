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

package org.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class MeterStatsEntry implements Serializable {

    @JsonProperty
    private long meterId;

    @JsonProperty
    private long byteInCount;

    @JsonProperty
    private long packetsInCount;

    @JsonCreator
    public MeterStatsEntry(@JsonProperty("meter_id") long meterId,
                           @JsonProperty("byte_in_count") long byteInCount,
                           @JsonProperty("packets_in_count") long packetsInCount) {
        this.byteInCount = byteInCount;
        this.packetsInCount = packetsInCount;
        this.meterId = meterId;
    }
}
