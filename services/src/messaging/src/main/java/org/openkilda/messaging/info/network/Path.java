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

package org.openkilda.messaging.info.network;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Path implements Serializable {
    @JsonProperty("bandwidth")
    private Long bandwidth;

    @JsonProperty("latency")
    private Long latency;

    @JsonProperty("edges")
    private List<String> edges;

    public Path(@JsonProperty("bandwidth") Long bandwidth,
                @JsonProperty("latency") Long latency,
                @JsonProperty("edges") List<String> edges) {
        this.bandwidth = bandwidth;
        this.latency = latency;
        this.edges = edges;
    }
}
