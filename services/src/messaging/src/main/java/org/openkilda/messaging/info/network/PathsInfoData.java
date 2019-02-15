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

package org.openkilda.messaging.info.network;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Builder
public class PathsInfoData extends InfoData {
    @JsonProperty("total_bandwidth")
    private Long totalBandwidth;

    @JsonProperty("paths")
    private List<Path> paths;

    public PathsInfoData(@JsonProperty("total_bandwidth") Long totalBandwidth,
                    @JsonProperty("paths") List<Path> paths) {
        this.totalBandwidth = totalBandwidth;
        this.paths = paths;
    }

}
