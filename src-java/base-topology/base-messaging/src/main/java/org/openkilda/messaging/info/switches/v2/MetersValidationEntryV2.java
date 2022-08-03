/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.info.switches.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class MetersValidationEntryV2 implements Serializable {
    @JsonProperty("as_expected")
    private boolean asExpected;

    @JsonProperty("excess")
    private List<MeterInfoEntryV2> excess;

    @JsonProperty("proper")
    private List<MeterInfoEntryV2> proper;

    @JsonProperty("missing")
    private List<MeterInfoEntryV2> missing;

    @JsonProperty("misconfigured")
    private List<MisconfiguredInfo<MeterInfoEntryV2>> misconfigured;

    //    @JsonCreator
    //    public void MetersValidationEntry(@JsonProperty("as_expected") boolean asExpected,
    //                                      @JsonProperty("missing") List<MeterInfoEntryV2> missing,
    //                                      @JsonProperty("misconfigured")
    //                                         List<MisconfiguredInfo<MeterInfoEntryV2>> misconfigured,
    //                                      @JsonProperty("proper") List<MeterInfoEntryV2> proper,
    //                                      @JsonProperty("excess") List<MeterInfoEntryV2> excess) {
    //        this.asExpected = asExpected;
    //        this.missing = missing;
    //        this.misconfigured = misconfigured;
    //        this.proper = proper;
    //        this.excess = excess;
    //    }
}
