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

package org.openkilda.northbound.dto.links;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class PathDto {

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("port_no")
    private int portNo;

    @JsonProperty("seq_id")
    private int seqId;

    @JsonProperty("segment_latency")
    private Long segLatency;

    public PathDto(@JsonProperty("switch_id") String switchId, @JsonProperty("port_no") int portNo,
                   @JsonProperty("seq_id") int seqId, @JsonProperty("segment_latency") Long segLatency) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.seqId = seqId;
        this.segLatency = segLatency;
    }
}
