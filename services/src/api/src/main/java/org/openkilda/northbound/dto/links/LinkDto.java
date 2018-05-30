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
import lombok.Value;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class LinkDto {

    @JsonProperty("speed")
    private long speed;

    @JsonProperty("available_bandwidth")
    private long availableBandwidth;

    @JsonProperty("state")
    protected LinkStatus state;

    @JsonProperty("path")
    private List<PathDto> path;

    public LinkDto(@JsonProperty("speed") long speed, @JsonProperty("available_bandwidth") long availableBandwidth,
                   @JsonProperty("state") LinkStatus state, @JsonProperty("path") List<PathDto> path) {
        this.speed = speed;
        this.availableBandwidth = availableBandwidth;
        this.state = state;
        this.path = path;
    }
}
