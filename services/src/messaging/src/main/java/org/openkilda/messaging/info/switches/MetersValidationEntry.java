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
import java.util.List;

@Data
@Builder
public class MetersValidationEntry implements Serializable {

    @JsonProperty("missing")
    private List<MeterInfoEntry> missing;

    @JsonProperty("misconfigured")
    private List<MeterInfoEntry> misconfigured;

    @JsonProperty("proper")
    private List<MeterInfoEntry> proper;

    @JsonProperty("excess")
    private List<Long> excess;

    @JsonCreator
    public MetersValidationEntry(@JsonProperty("missing") List<MeterInfoEntry> missing,
                                 @JsonProperty("misconfigured") List<MeterInfoEntry> misconfigured,
                                 @JsonProperty("proper") List<MeterInfoEntry> proper,
                                 @JsonProperty("excess") List<Long> excess) {
        this.missing = missing;
        this.misconfigured = misconfigured;
        this.proper = proper;
        this.excess = excess;
    }
}
