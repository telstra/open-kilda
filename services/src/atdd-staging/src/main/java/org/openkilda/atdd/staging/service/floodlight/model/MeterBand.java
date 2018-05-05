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

package org.openkilda.atdd.staging.service.floodlight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class MeterBand {

    private long rate;

    private long burstSize;

    private String version;

    private int type;

    public MeterBand(
            @JsonProperty("rate") long rate, @JsonProperty("burstSize") long burstSize,
            @JsonProperty("version") String version, @JsonProperty("type") int type) {
        this.rate = rate;
        this.burstSize = burstSize;
        this.version = version;
        this.type = type;
    }
}
