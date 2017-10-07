/* Copyright 2017 Telstra Open Source
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

package org.bitbucket.openkilda.messaging.info.stats;

import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * This class contains the flow stats replies for a given switch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "switch_id",
        "stats"})
public class FlowStatsData extends InfoData {

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty
    private List<FlowStatsReply> stats;

    public FlowStatsData(@JsonProperty("switch_id") String switchId,
                         @JsonProperty("stats") List<FlowStatsReply> switchStats) {
        this.switchId = switchId;
        this.stats = switchStats;
    }

    public String getSwitchId() {
        return switchId;
    }

    public List<FlowStatsReply> getStats() {
        return stats;
    }
}
