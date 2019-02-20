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

package org.openkilda.northbound.dto.network;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathsDto {
    @JsonProperty("total_bandwidth")
    private Long totalBandwidth;

    @JsonProperty("paths")
    private List<PathDto> paths;

    public PathsDto(@JsonProperty("total_bandwidth") Long totalBandwidth,
                    @JsonProperty("paths") List<PathDto> paths) {
        this.totalBandwidth = totalBandwidth;
        this.paths = paths;
    }
}
