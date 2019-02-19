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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkDto {

    @JsonProperty("latency_ns")
    protected long latency;

    @JsonProperty("speed")
    private long speed;

    @JsonProperty("available_bandwidth")
    private long availableBandwidth;

    @JsonProperty("max_bandwidth")
    private long maxBandwidth;

    @JsonProperty("default_max_bandwidth")
    private long defaultMaxBandwidth;

    @JsonProperty("state")
    protected LinkStatus state;

    @JsonProperty("actual_state")
    private LinkStatus actualState;

    @JsonProperty("cost")
    private int cost;

    @JsonProperty("under_maintenance")
    private boolean underMaintenance;

    @JsonProperty("enable_bfd")
    private boolean enableBfd;

    @JsonProperty("bfd_session_status")
    private String bfdSessionStatus;

    @JsonProperty("path")
    private List<PathDto> path;
}
